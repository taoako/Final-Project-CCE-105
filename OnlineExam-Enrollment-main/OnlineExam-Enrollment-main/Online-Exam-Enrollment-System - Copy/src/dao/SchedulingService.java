package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * SchedulingService provides an automatic way to assign a scheduled date/time
 * and room
 * for a student's enrolled exam using data stored directly in student_exams.
 *
 * Strategy:
 * 1. For a given (student, exam) fetch the exam's base date/time & duration.
 * 2. Build an occupancy map of (room -> list of occupied intervals) for that
 * date.
 * 3. Iterate candidate start times on a 30‑minute grid from exam_time (or
 * 09:00) until 17:00.
 * 4. For each start time test each room for overlap; choose first free.
 * 5. If nothing fits, fallback to base time + first room.
 * 6. Update student_exams row (scheduled_date, scheduled_time, room); set
 * status to 'Enrolled' if still Pending.
 */
public final class SchedulingService {

    private SchedulingService() {
    }

    private static final String[] ROOMS = {
            "Main Hall", "Room 101", "Room 102", "Room 103", "Computer Lab 1", "Computer Lab 2"
    };
    private static final LocalTime DAY_START = LocalTime.of(9, 0);
    private static final LocalTime DAY_END = LocalTime.of(17, 0);

    public static boolean autoScheduleExam(int studentId, int examId) {
        // Schedule every unscheduled row for this (student, exam). If at least one is
        // scheduled or already done, return true.
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null)
                return false;
            boolean any = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM student_exams WHERE student_id=? AND exam_id=? AND (scheduled_date IS NULL OR scheduled_time IS NULL OR room IS NULL) ORDER BY id")) {
                ps.setInt(1, studentId);
                ps.setInt(2, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean foundUnscheduled = false;
                    while (rs.next()) {
                        foundUnscheduled = true;
                        int seId = rs.getInt(1);
                        if (autoScheduleStudentExam(seId))
                            any = true;
                    }
                    if (!foundUnscheduled) {
                        // All rows already scheduled for this exam
                        return true;
                    }
                }
            }
            return any;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Batch scheduler that applies a PRIORITY QUEUE (min-heap) strategy across ALL
     * unscheduled student_exams rows.
     *
     * Priority order (highest precedence first):
     * 1. Earlier exam_date
     * 2. Longer duration (schedule long blocks first to reduce fragmentation)
     * 3. Earlier base exam_time
     * 4. Lower student_exams.id (stable tie-break)
     *
     * Data Structures Used:
     * - PriorityQueue<Candidate> (heap) for O(log n) extraction of next best exam.
     * - Map<LocalDate, Map<String, List<Interval>>> to maintain an in-memory
     * occupancy map per date and per room so we avoid repeated SQL queries for
     * conflicts; each interval list kept sorted by start time (binary-search
     * insertion potential, current implementation linear for simplicity).
     *
     * Advantages:
     * - Schedules hardest (longest) / most urgent (earliest date) exams first.
     * - Reduces fragmentation versus naive per-row greedy ordering.
     * - Eliminates per-row SELECT of all other intervals (reduces DB round trips).
     *
     * @return number of rows successfully scheduled in this batch run.
     */
    public static int scheduleAllPending() {
        int scheduledCount = 0;
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null)
                return 0;

            // 1. Load all unscheduled candidates
            PriorityQueue<Candidate> heap = new PriorityQueue<>(Comparator
                    .comparing((Candidate c) -> c.examDate)
                    .thenComparing((Candidate c) -> -c.durationMinutes) // longer first
                    .thenComparing(c -> c.baseTime)
                    .thenComparingInt(c -> c.studentExamId));

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT se.id AS se_id, se.student_id, se.exam_id, e.exam_date, e.exam_time, e.duration " +
                            "FROM student_exams se JOIN exams e ON se.exam_id = e.id " +
                            "WHERE (se.scheduled_date IS NULL OR se.scheduled_time IS NULL OR se.room IS NULL)")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        java.sql.Date d = rs.getDate("exam_date");
                        if (d == null)
                            continue; // cannot schedule without date
                        LocalDate date = d.toLocalDate();
                        Time t = rs.getTime("exam_time");
                        LocalTime baseTime = t != null ? t.toLocalTime() : DAY_START;
                        if (baseTime.isBefore(DAY_START) || baseTime.isAfter(DAY_END))
                            baseTime = DAY_START;
                        String dur = rs.getString("duration");
                        int durMin = parseDurationMinutes(dur != null ? dur : "2 hours");
                        Candidate c = new Candidate();
                        c.studentExamId = rs.getInt("se_id");
                        c.studentId = rs.getInt("student_id");
                        c.examId = rs.getInt("exam_id");
                        c.examDate = date;
                        c.baseTime = baseTime;
                        c.durationMinutes = durMin;
                        heap.add(c);
                    }
                }
            }

            if (heap.isEmpty())
                return 0;

            // 2. Occupancy structure: date -> room -> intervals
            Map<LocalDate, Map<String, List<Interval>>> calendar = new HashMap<>();

            while (!heap.isEmpty()) {
                Candidate c = heap.poll();
                Map<String, List<Interval>> dayMap = calendar.computeIfAbsent(c.examDate, k -> new HashMap<>());
                // Preload existing DB intervals for date lazily (first time we touch date)
                if (dayMap.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT scheduled_time, room, e.duration FROM student_exams se JOIN exams e ON se.exam_id = e.id "
                                    +
                                    "WHERE se.scheduled_date=? AND se.scheduled_time IS NOT NULL AND se.room IS NOT NULL")) {
                        ps.setDate(1, java.sql.Date.valueOf(c.examDate));
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                Time st = rs.getTime("scheduled_time");
                                String room = rs.getString("room");
                                String dStr = rs.getString("duration");
                                int durMin = parseDurationMinutes(dStr != null ? dStr : "2 hours");
                                if (st != null && room != null) {
                                    LocalTime start = st.toLocalTime();
                                    Interval in = new Interval();
                                    in.start = start;
                                    in.end = start.plusMinutes(durMin);
                                    in.room = room;
                                    dayMap.computeIfAbsent(room, r -> new ArrayList<>()).add(in);
                                }
                            }
                        }
                    }
                }

                // 3. Find slot via 30-min stepping & room iteration
                LocalTime chosenStart = null;
                String chosenRoom = null;
                for (LocalTime cursor = c.baseTime; !cursor.plusMinutes(c.durationMinutes)
                        .isAfter(DAY_END); cursor = cursor.plusMinutes(30)) {
                    LocalTime end = cursor.plusMinutes(c.durationMinutes);
                    for (String room : ROOMS) {
                        if (isRoomFree(dayMap, room, cursor, end)) {
                            chosenStart = cursor;
                            chosenRoom = room;
                            break;
                        }
                    }
                    if (chosenStart != null)
                        break;
                }
                if (chosenStart == null) { // fallback
                    chosenStart = c.baseTime;
                    chosenRoom = ROOMS[0];
                }

                // 4. Persist & update in-memory calendar
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE student_exams SET scheduled_date=?, scheduled_time=?, room=?, status=CASE WHEN status='Pending' THEN 'Enrolled' ELSE status END WHERE id=?")) {
                    upd.setDate(1, java.sql.Date.valueOf(c.examDate));
                    upd.setTime(2, Time.valueOf(chosenStart));
                    upd.setString(3, chosenRoom);
                    upd.setInt(4, c.studentExamId);
                    if (upd.executeUpdate() > 0) {
                        Interval in = new Interval();
                        in.start = chosenStart;
                        in.end = chosenStart.plusMinutes(c.durationMinutes);
                        in.room = chosenRoom;
                        dayMap.computeIfAbsent(chosenRoom, r -> new ArrayList<>()).add(in);
                        scheduledCount++;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return scheduledCount;
    }

    /**
     * Schedule a specific student_exams row by its primary key id.
     */
    public static boolean autoScheduleStudentExam(int studentExamId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null)
                return false;

            Integer studentId = null;
            Integer examId = null;
            LocalDate examDate = null;
            LocalTime baseTime = DAY_START;
            int durationMinutes = 120;
            boolean alreadyScheduled = false;

            // Fetch row + exam meta
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT se.student_id, se.exam_id, se.scheduled_date, se.scheduled_time, se.room, e.exam_date, e.exam_time, e.duration "
                            +
                            "FROM student_exams se JOIN exams e ON se.exam_id=e.id WHERE se.id=?")) {
                ps.setInt(1, studentExamId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next())
                        return false;
                    studentId = rs.getInt("student_id");
                    examId = rs.getInt("exam_id");
                    java.sql.Date dExam = rs.getDate("exam_date");
                    Time tExam = rs.getTime("exam_time");
                    String dur = rs.getString("duration");
                    java.sql.Date schedDate = rs.getDate("scheduled_date");
                    Time schedTime = rs.getTime("scheduled_time");
                    String room = rs.getString("room");
                    if (schedDate != null && schedTime != null && room != null) {
                        alreadyScheduled = true;
                    }
                    if (dExam != null)
                        examDate = dExam.toLocalDate();
                    if (tExam != null)
                        baseTime = tExam.toLocalTime();
                    if (dur != null)
                        durationMinutes = parseDurationMinutes(dur);
                }
            }
            if (alreadyScheduled)
                return true;
            if (examDate == null)
                return false;
            if (baseTime.isBefore(DAY_START) || baseTime.isAfter(DAY_END))
                baseTime = DAY_START;

            // Build busy intervals for that date excluding this row
            List<Interval> busy = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT scheduled_time, room FROM student_exams WHERE scheduled_date=? AND scheduled_time IS NOT NULL AND room IS NOT NULL AND id<>?")) {
                ps.setDate(1, java.sql.Date.valueOf(examDate));
                ps.setInt(2, studentExamId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Time st = rs.getTime("scheduled_time");
                        String rm = rs.getString("room");
                        if (st != null && rm != null) {
                            Interval in = new Interval();
                            in.start = st.toLocalTime();
                            in.end = in.start.plusMinutes(durationMinutes);
                            in.room = rm;
                            busy.add(in);
                        }
                    }
                }
            }

            LocalTime chosenStart = null;
            String chosenRoom = null;
            for (LocalTime cursor = baseTime; !cursor.plusMinutes(durationMinutes).isAfter(DAY_END); cursor = cursor
                    .plusMinutes(30)) {
                LocalTime end = cursor.plusMinutes(durationMinutes);
                for (String room : ROOMS) {
                    if (roomFree(room, cursor, end, busy)) {
                        chosenStart = cursor;
                        chosenRoom = room;
                        break;
                    }
                }
                if (chosenStart != null)
                    break;
            }
            if (chosenStart == null) {
                chosenStart = baseTime;
                chosenRoom = ROOMS[0];
            }

            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE student_exams SET scheduled_date=?, scheduled_time=?, room=?, status=CASE WHEN status='Pending' THEN 'Enrolled' ELSE status END WHERE id=?")) {
                upd.setDate(1, java.sql.Date.valueOf(examDate));
                upd.setTime(2, Time.valueOf(chosenStart));
                upd.setString(3, chosenRoom);
                upd.setInt(4, studentExamId);
                return upd.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean roomFree(String room, LocalTime start, LocalTime end, List<Interval> intervals) {
        for (Interval in : intervals) {
            if (!in.room.equals(room))
                continue;
            if (start.isBefore(in.end) && in.start.isBefore(end))
                return false; // overlap
        }
        return true;
    }

    private static class Interval {
        LocalTime start;
        LocalTime end;
        String room;
    }

    private static class Candidate {
        int studentExamId;
        int studentId;
        int examId;
        LocalDate examDate;
        LocalTime baseTime;
        int durationMinutes;
    }

    private static boolean isRoomFree(Map<LocalDate, Map<String, List<Interval>>> calendar, LocalDate date, String room,
            LocalTime start, LocalTime end) {
        Map<String, List<Interval>> day = calendar.get(date);
        if (day == null)
            return true;
        return isRoomFree(day, room, start, end);
    }

    private static boolean isRoomFree(Map<String, List<Interval>> dayMap, String room, LocalTime start, LocalTime end) {
        List<Interval> list = dayMap.get(room);
        if (list == null)
            return true;
        for (Interval in : list) {
            if (start.isBefore(in.end) && in.start.isBefore(end))
                return false;
        }
        return true;
    }

    private static int parseDurationMinutes(String txt) {
        String d = txt.toLowerCase();
        // basic patterns like "2 hours", "1.5 hours" etc.
        if (d.contains("1.5"))
            return 90;
        if (d.contains("2.5"))
            return 150;
        if (d.contains("3"))
            return 180;
        if (d.contains("2"))
            return 120;
        if (d.contains("1"))
            return 60;
        // fallback: extract leading number
        try {
            return Integer.parseInt(d.replaceAll("[^0-9]", "").trim());
        } catch (Exception ignored) {
        }
        return 120;
    }
}
