package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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
