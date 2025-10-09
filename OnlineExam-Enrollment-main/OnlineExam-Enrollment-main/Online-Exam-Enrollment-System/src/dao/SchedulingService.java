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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

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

    public static boolean smartScheduleStudentExam(int studentExamId, Connection externalConn) {
        Connection conn = externalConn;
        boolean created = false;
        try {
            if (conn == null) {
                conn = DatabaseConnection.getConnection();
                created = true;
            }
            if (conn == null)
                return false;

            LocalDate examDate = null;
            LocalTime baseTime = DAY_START;
            int durationMin = 120;
            boolean already = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT se.scheduled_date, se.scheduled_time, se.room, e.exam_date, e.exam_time, e.duration FROM student_exams se JOIN exams e ON se.exam_id=e.id WHERE se.id=?")) {
                ps.setInt(1, studentExamId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next())
                        return false;
                    java.sql.Date schedD = rs.getDate("scheduled_date");
                    Time schedT = rs.getTime("scheduled_time");
                    String schedRoom = rs.getString("room");
                    if (schedD != null && schedT != null && schedRoom != null)
                        already = true;
                    java.sql.Date d = rs.getDate("exam_date");
                    if (d != null)
                        examDate = d.toLocalDate();
                    Time t = rs.getTime("exam_time");
                    if (t != null)
                        baseTime = t.toLocalTime();
                    String dur = rs.getString("duration");
                    if (dur != null)
                        durationMin = parseDurationMinutes(dur);
                }
            }
            if (already)
                return true;
            if (examDate == null)
                return false;
            if (baseTime.isBefore(DAY_START) || baseTime.isAfter(DAY_END))
                baseTime = DAY_START;

            Map<String, java.util.TreeMap<LocalTime, LocalTime>> roomSchedules = new HashMap<>();
            for (String r : ROOMS)
                roomSchedules.put(r, new java.util.TreeMap<>());
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT se.scheduled_time, se.room, e.duration FROM student_exams se JOIN exams e ON se.exam_id=e.id WHERE se.scheduled_date=? AND se.scheduled_time IS NOT NULL AND se.room IS NOT NULL AND se.id<>?")) {
                ps.setDate(1, java.sql.Date.valueOf(examDate));
                ps.setInt(2, studentExamId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Time st = rs.getTime("scheduled_time");
                        String room = rs.getString("room");
                        String dStr = rs.getString("duration");
                        int dMin = parseDurationMinutes(dStr != null ? dStr : "2 hours");
                        if (st != null && room != null) {
                            LocalTime start = st.toLocalTime();
                            roomSchedules.get(room).put(start, start.plusMinutes(dMin));
                        }
                    }
                }
            }

            PriorityQueue<LocalTime> candidates = new PriorityQueue<>();
            for (LocalTime t = baseTime; !t.plusMinutes(durationMin).isAfter(DAY_END); t = t.plusMinutes(30))
                candidates.add(t);
            if (candidates.isEmpty())
                candidates.add(baseTime);

            LocalTime chosenStart = null;
            String chosenRoom = null;
            LocalTime chosenEnd = null;
            while (!candidates.isEmpty() && chosenStart == null) {
                LocalTime start = candidates.poll();
                LocalTime end = start.plusMinutes(durationMin);
                int bestLoad = Integer.MAX_VALUE;
                String bestRoom = null;
                for (String room : ROOMS) {
                    java.util.TreeMap<LocalTime, LocalTime> sched = roomSchedules.get(room);
                    java.util.Map.Entry<LocalTime, LocalTime> before = sched.floorEntry(start);
                    java.util.Map.Entry<LocalTime, LocalTime> after = sched.ceilingEntry(start);
                    boolean conflict = false;
                    if (before != null && before.getValue().isAfter(start))
                        conflict = true;
                    if (!conflict && after != null && end.isAfter(after.getKey()))
                        conflict = true;
                    if (!conflict) {
                        int load = sched.size();
                        if (load < bestLoad) {
                            bestLoad = load;
                            bestRoom = room;
                        }
                    }
                }
                if (bestRoom != null) {
                    chosenStart = start;
                    chosenEnd = start.plusMinutes(durationMin);
                    chosenRoom = bestRoom;
                }
            }
            if (chosenStart == null) {
                chosenStart = baseTime;
                chosenEnd = chosenStart.plusMinutes(durationMin);
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
        } finally {
            if (created && conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public static boolean smartScheduleStudentExam(int studentExamId) {
        return smartScheduleStudentExam(studentExamId, null);
    }

    public static class AssignmentResult {
        public int registrationId; // student_exams.id
        public int examScheduleId; // exam_schedules.id
        public LocalDate date; // scheduled_date
        public LocalTime start; // scheduled_time
        public String room; // room_number
        public Integer capacity; // optional capacity of the schedule
    }

    private static class CandidateSlot {
        LocalTime start;
        LocalTime end;
        String room;
        int roomUsage;
    }

    public static AssignmentResult scheduleAndEnrollExam(int studentId, int examId, Connection conn)
            throws SQLException {
        if (conn == null)
            throw new SQLException("Connection required");

        // 1. Check if student is already enrolled in this exam
        try (PreparedStatement checkPs = conn.prepareStatement(
                "SELECT 1 FROM student_exams se JOIN exam_schedules es ON se.exam_schedule_id=es.id WHERE se.student_id=? AND es.exam_id=?")) {
            checkPs.setInt(1, studentId);
            checkPs.setInt(2, examId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    throw new SQLException("Student is already enrolled in this exam");
                }
            }
        }

        // 2. Get student's existing exam schedule (conflict detection)
        Set<TimeSlot> studentSchedule = getStudentSchedule(studentId, conn);

        // 3. Get exam duration for conflict calculations
        int examDurationMinutes = getExamDuration(examId, conn);

        // 4. Try to find a conflict-free existing schedule
        ConflictFreeSchedule bestSchedule = findConflictFreeSchedule(studentId, examId, studentSchedule,
                examDurationMinutes, conn);

        Integer scheduleId = null;
        String scheduleRoom = null;
        java.sql.Date scheduleDate = null;
        java.sql.Time scheduleTime = null;

        if (bestSchedule != null) {
            // Found a conflict-free schedule with available capacity
            scheduleId = bestSchedule.scheduleId;
            scheduleRoom = bestSchedule.roomName;
            scheduleDate = bestSchedule.date;
            scheduleTime = bestSchedule.time;
        } else {
            // No conflict-free schedule found - create a new one using intelligent
            // scheduling
            NewScheduleResult newSchedule = createIntelligentSchedule(studentId, examId, studentSchedule,
                    examDurationMinutes, conn);
            if (newSchedule != null) {
                scheduleId = newSchedule.scheduleId;
                scheduleRoom = newSchedule.roomName;
                scheduleDate = newSchedule.date;
                scheduleTime = newSchedule.time;
            }
        }

        if (scheduleId == null) {
            throw new SQLException(
                    "❌ Cannot schedule exam - all time slots conflict with your existing exams. Please contact administrator to resolve scheduling conflicts.");
        }

        // 5. Enroll student in the found/created schedule
        int registrationId;
        try (PreparedStatement insertPs = conn.prepareStatement(
                "INSERT INTO student_exams (student_id, exam_schedule_id, status, is_paid) VALUES (?, ?, 'Enrolled', 1)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            insertPs.setInt(1, studentId);
            insertPs.setInt(2, scheduleId);
            insertPs.executeUpdate();

            try (ResultSet gk = insertPs.getGeneratedKeys()) {
                gk.next();
                registrationId = gk.getInt(1);
            }
        }

        AssignmentResult ar = new AssignmentResult();
        ar.registrationId = registrationId;
        ar.examScheduleId = scheduleId;
        ar.date = scheduleDate != null ? scheduleDate.toLocalDate() : LocalDate.now();
        ar.start = scheduleTime != null ? scheduleTime.toLocalTime() : LocalTime.now();
        ar.room = scheduleRoom;
        ar.capacity = -1; // Will be determined by exam_schedules.capacity
        return ar;
    }

    // ===== CONFLICT RESOLUTION DATA STRUCTURES =====

    /**
     * TimeSlot represents a time interval for conflict detection
     * Uses TreeMap internally for efficient interval overlap detection
     */
    static class TimeSlot implements Comparable<TimeSlot> {
        LocalDate date;
        LocalTime startTime;
        LocalTime endTime;
        String examName;
        String room;

        TimeSlot(LocalDate date, LocalTime start, LocalTime end, String examName, String room) {
            this.date = date;
            this.startTime = start;
            this.endTime = end;
            this.examName = examName;
            this.room = room;
        }

        // Check if this slot overlaps with another
        boolean overlapsWith(TimeSlot other) {
            if (!this.date.equals(other.date))
                return false;
            return this.startTime.isBefore(other.endTime) && this.endTime.isAfter(other.startTime);
        }

        @Override
        public int compareTo(TimeSlot other) {
            int dateComp = this.date.compareTo(other.date);
            if (dateComp != 0)
                return dateComp;
            return this.startTime.compareTo(other.startTime);
        }

        @Override
        public String toString() {
            return String.format("%s %s-%s (%s in %s)", date, startTime, endTime, examName, room);
        }
    }

    /**
     * ConflictFreeSchedule represents a schedule slot that doesn't conflict with
     * student's existing exams
     */
    static class ConflictFreeSchedule {
        int scheduleId;
        String roomName;
        java.sql.Date date;
        java.sql.Time time;
        int availableCapacity;
        int conflictScore; // Lower is better

        ConflictFreeSchedule(int scheduleId, String roomName, java.sql.Date date, java.sql.Time time,
                int availableCapacity) {
            this.scheduleId = scheduleId;
            this.roomName = roomName;
            this.date = date;
            this.time = time;
            this.availableCapacity = availableCapacity;
            this.conflictScore = 0;
        }
    }

    /**
     * NewScheduleResult represents a newly created schedule
     */
    static class NewScheduleResult {
        int scheduleId;
        String roomName;
        java.sql.Date date;
        java.sql.Time time;

        NewScheduleResult(int scheduleId, String roomName, java.sql.Date date, java.sql.Time time) {
            this.scheduleId = scheduleId;
            this.roomName = roomName;
            this.date = date;
            this.time = time;
        }
    }

    // ===== CONFLICT DETECTION & RESOLUTION METHODS =====

    /**
     * Gets all scheduled exams for a student using TreeSet for efficient conflict
     * detection
     */
    private static Set<TimeSlot> getStudentSchedule(int studentId, Connection conn) throws SQLException {
        Set<TimeSlot> schedule = new HashSet<>();

        String sql = "SELECT es.scheduled_date, es.scheduled_time, e.exam_name, e.duration, r.room_name " +
                "FROM student_exams se " +
                "JOIN exam_schedules es ON se.exam_schedule_id = es.id " +
                "JOIN exams e ON es.exam_id = e.id " +
                "JOIN rooms r ON es.room_id = r.id " +
                "WHERE se.student_id = ? AND es.scheduled_date IS NOT NULL";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("scheduled_date").toLocalDate();
                    LocalTime startTime = rs.getTime("scheduled_time").toLocalTime();
                    String durationStr = rs.getString("duration");
                    int durationMinutes = parseDurationMinutes(durationStr != null ? durationStr : "2 hours");
                    LocalTime endTime = startTime.plusMinutes(durationMinutes);
                    String examName = rs.getString("exam_name");
                    String roomName = rs.getString("room_name");

                    schedule.add(new TimeSlot(date, startTime, endTime, examName, roomName));
                }
            }
        }
        return schedule;
    }

    /**
     * Gets exam duration in minutes
     */
    private static int getExamDuration(int examId, Connection conn) throws SQLException {
        String sql = "SELECT duration FROM exams WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String durationStr = rs.getString("duration");
                    return parseDurationMinutes(durationStr != null ? durationStr : "2 hours");
                }
            }
        }
        return 120; // Default 2 hours
    }

    /**
     * Finds a conflict-free existing schedule using PriorityQueue for optimal
     * selection
     */
    private static ConflictFreeSchedule findConflictFreeSchedule(int studentId, int examId,
            Set<TimeSlot> studentSchedule,
            int examDurationMinutes, Connection conn) throws SQLException {

        // Priority queue to find best schedule (least enrolled first)
        PriorityQueue<ConflictFreeSchedule> candidateSchedules = new PriorityQueue<>(
                Comparator.comparingInt((ConflictFreeSchedule s) -> s.conflictScore)
                        .thenComparingInt(s -> -s.availableCapacity) // Higher capacity preferred
        );

        String sql = "SELECT es.id, r.room_name, es.scheduled_date, es.scheduled_time, es.capacity, " +
                "(SELECT COUNT(*) FROM student_exams se WHERE se.exam_schedule_id = es.id) AS enrolled " +
                "FROM exam_schedules es " +
                "JOIN rooms r ON es.room_id = r.id " +
                "WHERE es.exam_id = ? AND es.capacity > (SELECT COUNT(*) FROM student_exams se WHERE se.exam_schedule_id = es.id)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int scheduleId = rs.getInt("id");
                    String roomName = rs.getString("room_name");
                    java.sql.Date scheduleDate = rs.getDate("scheduled_date");
                    java.sql.Time scheduleTime = rs.getTime("scheduled_time");
                    int capacity = rs.getInt("capacity");
                    int enrolled = rs.getInt("enrolled");

                    // Create proposed time slot for this schedule
                    LocalDate date = scheduleDate.toLocalDate();
                    LocalTime startTime = scheduleTime.toLocalTime();
                    LocalTime endTime = startTime.plusMinutes(examDurationMinutes);
                    TimeSlot proposedSlot = new TimeSlot(date, startTime, endTime, "NEW_EXAM", roomName);

                    // Check for conflicts with student's existing schedule
                    boolean hasConflict = false;
                    for (TimeSlot existingSlot : studentSchedule) {
                        if (proposedSlot.overlapsWith(existingSlot)) {
                            hasConflict = true;
                            break;
                        }
                    }

                    if (!hasConflict) {
                        ConflictFreeSchedule candidate = new ConflictFreeSchedule(
                                scheduleId, roomName, scheduleDate, scheduleTime, capacity - enrolled);
                        candidate.conflictScore = enrolled; // Lower enrollment = better score
                        candidateSchedules.offer(candidate);
                    }
                }
            }
        }

        return candidateSchedules.poll(); // Return best candidate or null
    }

    /**
     * Creates a new intelligent schedule using TreeMap for time slot management
     * Implements sophisticated conflict avoidance algorithm
     */
    private static NewScheduleResult createIntelligentSchedule(int studentId, int examId, Set<TimeSlot> studentSchedule,
            int examDurationMinutes, Connection conn) throws SQLException {

        // TreeMap for organized time slot exploration
        Map<LocalDate, Set<LocalTime>> availableSlots = new java.util.TreeMap<>();

        // Generate potential dates (next 30 days)
        LocalDate startDate = LocalDate.now().plusDays(1);
        for (int i = 0; i < 30; i++) {
            LocalDate testDate = startDate.plusDays(i);
            Set<LocalTime> timeSlots = new HashSet<>();

            // Generate time slots from 9 AM to 5 PM
            for (LocalTime time = LocalTime.of(9, 0); time.plusMinutes(examDurationMinutes)
                    .isBefore(LocalTime.of(17, 1)); time = time.plusMinutes(30)) {
                timeSlots.add(time);
            }
            availableSlots.put(testDate, timeSlots);
        }

        // Remove conflicting time slots
        for (TimeSlot studentSlot : studentSchedule) {
            Set<LocalTime> daySlots = availableSlots.get(studentSlot.date);
            if (daySlots != null) {
                // Remove all slots that would overlap
                daySlots.removeIf(time -> {
                    LocalTime endTime = time.plusMinutes(examDurationMinutes);
                    return time.isBefore(studentSlot.endTime) && endTime.isAfter(studentSlot.startTime);
                });
            }
        }

        // Find best available slot with room
        for (Map.Entry<LocalDate, Set<LocalTime>> entry : availableSlots.entrySet()) {
            LocalDate date = entry.getKey();
            for (LocalTime time : entry.getValue()) {
                // Find available room for this time slot
                String availableRoom = findAvailableRoom(date, time, examDurationMinutes, conn);
                if (availableRoom != null) {
                    // Create new schedule
                    int newScheduleId = createNewSchedule(examId, availableRoom, date, time, conn);
                    if (newScheduleId > 0) {
                        return new NewScheduleResult(
                                newScheduleId,
                                availableRoom,
                                java.sql.Date.valueOf(date),
                                java.sql.Time.valueOf(time));
                    }
                }
            }
        }

        return null; // No available slot found
    }

    /**
     * Finds an available room for a specific date and time
     */
    private static String findAvailableRoom(LocalDate date, LocalTime time, int durationMinutes, Connection conn)
            throws SQLException {
        // Check each room for availability
        for (String room : ROOMS) {
            if (isRoomAvailable(room, date, time, durationMinutes, conn)) {
                return room;
            }
        }
        return null;
    }

    /**
     * Checks if a room is available for a specific time period
     */
    private static boolean isRoomAvailable(String roomName, LocalDate date, LocalTime startTime, int durationMinutes,
            Connection conn) throws SQLException {
        LocalTime endTime = startTime.plusMinutes(durationMinutes);

        String sql = "SELECT COUNT(*) FROM exam_schedules es " +
                "JOIN rooms r ON es.room_id = r.id " +
                "WHERE r.room_name = ? AND es.scheduled_date = ? " +
                "AND ((es.scheduled_time < ? AND DATE_ADD(es.scheduled_time, INTERVAL (SELECT COALESCE(NULLIF(SUBSTRING_INDEX(e.duration, ' ', 1), ''), '120') FROM exams e WHERE e.id = es.exam_id) MINUTE) > ?) "
                +
                "OR (es.scheduled_time < ? AND es.scheduled_time >= ?))";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName);
            ps.setDate(2, java.sql.Date.valueOf(date));
            ps.setTime(3, java.sql.Time.valueOf(endTime));
            ps.setTime(4, java.sql.Time.valueOf(startTime));
            ps.setTime(5, java.sql.Time.valueOf(endTime));
            ps.setTime(6, java.sql.Time.valueOf(startTime));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0; // Available if no conflicts
                }
            }
        }
        return false;
    }

    /**
     * Creates a new exam schedule in the database
     */
    private static int createNewSchedule(int examId, String roomName, LocalDate date, LocalTime time, Connection conn)
            throws SQLException {
        // Get room ID
        int roomId = 0;
        String getRoomIdSql = "SELECT id FROM rooms WHERE room_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(getRoomIdSql)) {
            ps.setString(1, roomName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    roomId = rs.getInt("id");
                }
            }
        }

        if (roomId == 0) {
            throw new SQLException("Room not found: " + roomName);
        }

        // Insert new schedule
        String insertSql = "INSERT INTO exam_schedules (exam_id, room_id, scheduled_date, scheduled_time, capacity) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, examId);
            ps.setInt(2, roomId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            ps.setTime(4, java.sql.Time.valueOf(time));
            ps.setInt(5, 30); // Default capacity

            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    return gk.getInt(1);
                }
            }
        }
        return 0;
    }

    // ---- Capacity column support helpers ----
    // Note: Database now has capacity column built-in, these are legacy methods
    private static boolean capacityCheckDone = false;
    private static boolean hasCapacityColumn = true; // Always true now

    private static synchronized void ensureCapacityColumnIfNeeded(Connection conn) {
        // No-op: capacity column is guaranteed to exist in aligned schema
        capacityCheckDone = true;
        hasCapacityColumn = true;
    }

    private static boolean studentHasSlot(int studentId, LocalDate date, int slotId, Connection conn)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM exam_schedules es JOIN student_exams se ON se.exam_schedule_id=es.id WHERE se.student_id=? AND es.scheduled_date=? AND es.time_slot_id=? LIMIT 1")) {
            ps.setInt(1, studentId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            ps.setInt(3, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String pickAvailableRoom(LocalDate date, int slotId, LocalTime start, int durationMin,
            Connection conn)
            throws SQLException {
        // Rooms already occupied in this slot/date
        Map<String, Boolean> occupied = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT r.room_name FROM exam_schedules es JOIN rooms r ON r.id=es.room_id WHERE es.scheduled_date=?")) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    occupied.put(rs.getString(1), true);
                }
            }
        }
        // Choose a room not yet used this slot; prefer lowest current usage that day
        String chosen = null;
        int bestUsage = Integer.MAX_VALUE;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT r.room_name, (SELECT COUNT(*) FROM exam_schedules es WHERE es.room_id=r.id AND es.scheduled_date=?) AS usage_count FROM rooms r ORDER BY r.capacity DESC")) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rn = rs.getString(1);
                    int usage = rs.getInt(2);
                    if (occupied.containsKey(rn))
                        continue;
                    if (usage < bestUsage) {
                        bestUsage = usage;
                        chosen = rn;
                    }
                }
            }
        }
        return chosen; // null if none free
    }

    private static int fetchExamDurationMinutes(int examId, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT duration FROM exams WHERE id=?")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String dur = rs.getString(1);
                    return parseDurationMinutes(dur != null ? dur : "2 hours");
                }
            }
        }
        return 120;
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

    // Helper methods for aligned database schema
    private static boolean studentHasSlotAligned(int studentId, LocalDate date, int slotId, Connection conn)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM exam_schedules es JOIN student_exams se ON se.exam_schedule_id=es.id " +
                        "WHERE se.student_id=? AND es.scheduled_date=? LIMIT 1")) {
            ps.setInt(1, studentId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static class RoomInfo {
        int id;
        String name;
        int capacity;
    }

    private static RoomInfo pickAvailableRoomAligned(LocalDate date, int slotId, Connection conn)
            throws SQLException {
        // Get rooms not yet used in this specific time slot on this date
        Set<Integer> occupiedRoomIds = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT room_id FROM exam_schedules WHERE scheduled_date=?")) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    occupiedRoomIds.add(rs.getInt("room_id"));
                }
            }
        }

        // Choose available room with lowest usage count for the day
        RoomInfo chosen = null;
        int bestUsage = Integer.MAX_VALUE;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT r.id, r.room_name, r.capacity, " +
                        "(SELECT COUNT(*) FROM exam_schedules es WHERE es.room_id=r.id AND es.scheduled_date=?) AS usage_count "
                        +
                        "FROM rooms r ORDER BY r.capacity DESC")) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int roomId = rs.getInt("id");
                    if (occupiedRoomIds.contains(roomId))
                        continue; // room already used in some slot this day

                    int usage = rs.getInt("usage_count");
                    if (usage < bestUsage) {
                        bestUsage = usage;
                        chosen = new RoomInfo();
                        chosen.id = roomId;
                        chosen.name = rs.getString("room_name");
                        chosen.capacity = rs.getInt("capacity");
                    }
                }
            }
        }
        return chosen; // null if none free
    }

    // ==============================================================
    // ADVANCED TESTING-CENTER SCHEDULER (FAIR / LOAD-BALANCED VERSION)
    // ==============================================================
    // This method is an alternative to scheduleAndEnrollExam focused on:
    // - Reusing existing schedules until capacity is reached
    // - Balancing room usage (fewest occupied slots first)
    // - Preventing overlapping sessions for the same student per day
    // - Using TreeMap<LocalTime, LocalTime> per room (fast neighbor conflict check)
    // - Searching forward day-by-day up to a horizon (default 30 days)
    // - Respecting time_slots table; if empty, falls back to 4 canonical slots
    // - Minimal new schedule creation – only when no capacity remains
    // - Capacity comes from exam_schedules.capacity (if column) else rooms.capacity
    // Call this instead of scheduleAndEnrollExam if you want the newer strategy.
    public static AssignmentResult scheduleExamTestingCenter(int studentId, int examId, Connection external)
            throws SQLException {
        boolean created = false;
        Connection conn = external;
        if (conn == null) {
            conn = DatabaseConnection.getConnection();
            created = true;
        }
        try {
            if (conn == null)
                throw new SQLException("No connection");
            ensureCapacityColumnIfNeeded(conn);
            int durationMin = fetchExamDurationMinutes(examId, conn);
            List<TimeSlotDef> slots = loadTimeSlots(conn);
            LocalDate today = LocalDate.now();
            int horizonDays = 30;

            for (int offset = 0; offset < horizonDays; offset++) {
                LocalDate date = today.plusDays(offset);
                // Skip date if student already has all slots occupied (quick check)
                if (studentFullyBookedAllSlots(studentId, date, conn, slots))
                    continue;

                // Build room usage + schedules for that date using TreeMaps for conflicts
                Map<String, java.util.TreeMap<LocalTime, LocalTime>> roomMaps = new HashMap<>();
                Map<String, Integer> roomUsage = new HashMap<>();
                List<String> rooms = loadRooms(conn);
                for (String r : rooms) {
                    roomMaps.put(r, new java.util.TreeMap<>());
                    roomUsage.put(r, 0);
                }

                // Load existing schedules for the day
                try (PreparedStatement ps = conn.prepareStatement(
                        hasCapacityColumn
                                ? "SELECT es.id, es.exam_id, es.room_number, es.scheduled_time, COALESCE(es.capacity, r.capacity) AS capacity, (SELECT COUNT(*) FROM student_exams se WHERE se.exam_schedule_id=es.id) AS enrolled, es.time_slot_id FROM exam_schedules es JOIN rooms r ON r.room_name=es.room_number WHERE es.scheduled_date=?"
                                : "SELECT es.id, es.exam_id, es.room_number, es.scheduled_time, r.capacity AS capacity, (SELECT COUNT(*) FROM student_exams se WHERE se.exam_schedule_id=es.id) AS enrolled, es.time_slot_id FROM exam_schedules es JOIN rooms r ON r.room_name=es.room_number WHERE es.scheduled_date=?")) {
                    ps.setDate(1, java.sql.Date.valueOf(date));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String room = rs.getString("room_number");
                            Time st = rs.getTime("scheduled_time");
                            if (room != null && st != null) {
                                LocalTime start = st.toLocalTime();
                                roomMaps.get(room).put(start, start.plusMinutes(durationMin));
                                roomUsage.put(room, roomUsage.get(room) + 1);
                            }
                        }
                    }
                }

                for (TimeSlotDef slot : slots) {
                    // Skip if student already has exam in this slot/date
                    if (studentHasSlot(studentId, date, slot.id, conn))
                        continue;

                    // 1. Try to reuse an existing schedule for SAME exam & slot with free capacity
                    Integer reuseScheduleId = null;
                    String reuseRoom = null;
                    Integer reuseCapacity = null;
                    try (PreparedStatement ps = conn.prepareStatement(
                            hasCapacityColumn
                                    ? "SELECT es.id, es.room_number, COALESCE(es.capacity, r.capacity) AS capacity, (SELECT COUNT(*) FROM student_exams se WHERE se.exam_schedule_id=es.id) AS enrolled FROM exam_schedules es JOIN rooms r ON r.room_name=es.room_number WHERE es.exam_id=? AND es.scheduled_date=? AND es.time_slot_id=? ORDER BY enrolled ASC"
                                    : "SELECT es.id, es.room_number, r.capacity AS capacity, (SELECT COUNT(*) FROM student_exams se WHERE se.exam_schedule_id=es.id) AS enrolled FROM exam_schedules es JOIN rooms r ON r.room_name=es.room_number WHERE es.exam_id=? AND es.scheduled_date=? AND es.time_slot_id=? ORDER BY enrolled ASC")) {
                        ps.setInt(1, examId);
                        ps.setDate(2, java.sql.Date.valueOf(date));
                        ps.setInt(3, slot.id);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int cap = rs.getInt("capacity");
                                int enrolled = rs.getInt("enrolled");
                                if (enrolled < cap) {
                                    reuseScheduleId = rs.getInt("id");
                                    reuseRoom = rs.getString("room_number");
                                    reuseCapacity = cap;
                                    break;
                                }
                            }
                        }
                    }
                    if (reuseScheduleId != null) {
                        // Enroll directly WITHOUT changing any schedule fields
                        // Read the actual schedule date/time from DB to avoid any mismatch
                        LocalDate actualDate = date;
                        LocalTime actualTime = slot.start;
                        try (PreparedStatement ps2 = conn.prepareStatement(
                                "SELECT scheduled_date, scheduled_time FROM exam_schedules WHERE id=?")) {
                            ps2.setInt(1, reuseScheduleId);
                            try (ResultSet rs2 = ps2.executeQuery()) {
                                if (rs2.next()) {
                                    java.sql.Date d = rs2.getDate("scheduled_date");
                                    java.sql.Time t = rs2.getTime("scheduled_time");
                                    if (d != null)
                                        actualDate = d.toLocalDate();
                                    if (t != null)
                                        actualTime = t.toLocalTime();
                                }
                            }
                        }

                        int regId = enrollStudentIntoSchedule(studentId, reuseScheduleId, conn);
                        AssignmentResult ar = new AssignmentResult();
                        ar.registrationId = regId;
                        ar.examScheduleId = reuseScheduleId;
                        ar.date = actualDate;
                        ar.start = actualTime;
                        ar.room = reuseRoom;
                        ar.capacity = reuseCapacity;
                        return ar;
                    }

                    // 2. Create a new schedule if a room is free at this slot
                    LocalTime desiredStart = slot.start;
                    // Choose best room = conflict-free & lowest current usage
                    String bestRoom = null;
                    int bestLoad = Integer.MAX_VALUE;
                    for (String room : rooms) {
                        java.util.TreeMap<LocalTime, LocalTime> map = roomMaps.get(room);
                        if (conflict(map, desiredStart, desiredStart.plusMinutes(durationMin)))
                            continue;
                        int load = roomUsage.get(room);
                        if (load < bestLoad) {
                            bestLoad = load;
                            bestRoom = room;
                        }
                    }
                    if (bestRoom == null)
                        continue; // all rooms busy at this slot -> next slot

                    // Determine capacity from room
                    int cap = fetchRoomCapacity(bestRoom, conn);
                    Integer newScheduleId;
                    try (PreparedStatement ins = conn.prepareStatement(
                            hasCapacityColumn
                                    ? "INSERT INTO exam_schedules (student_id, exam_id, room_number, scheduled_date, scheduled_time, time_slot_id, capacity) VALUES (?,?,?,?,?,?,?)"
                                    : "INSERT INTO exam_schedules (student_id, exam_id, room_number, scheduled_date, scheduled_time, time_slot_id) VALUES (?,?,?,?,?,?)",
                            PreparedStatement.RETURN_GENERATED_KEYS)) {
                        ins.setInt(1, studentId); // creator
                        ins.setInt(2, examId);
                        ins.setString(3, bestRoom);
                        ins.setDate(4, java.sql.Date.valueOf(date));
                        ins.setTime(5, Time.valueOf(desiredStart));
                        ins.setInt(6, slot.id);
                        if (hasCapacityColumn)
                            ins.setInt(7, cap);
                        ins.executeUpdate();
                        try (ResultSet gk = ins.getGeneratedKeys()) {
                            gk.next();
                            newScheduleId = gk.getInt(1);
                        }
                    }
                    // Update structures for fairness if more scheduling happens same invocation
                    roomMaps.get(bestRoom).put(desiredStart, desiredStart.plusMinutes(durationMin));
                    roomUsage.put(bestRoom, roomUsage.get(bestRoom) + 1);

                    // Enroll (creator may or may not already be implicitly enrolled; we keep
                    // explicit)
                    int regId = enrollStudentIntoSchedule(studentId, newScheduleId, conn);
                    AssignmentResult ar = new AssignmentResult();
                    ar.registrationId = regId;
                    ar.examScheduleId = newScheduleId;
                    ar.date = date;
                    ar.start = desiredStart;
                    ar.room = bestRoom;
                    ar.capacity = cap;
                    return ar;
                }
            }
            throw new SQLException("No capacity available in the next " + horizonDays + " days");
        } finally {
            if (created && conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // ---------- Helpers for advanced scheduler ----------
    private static boolean conflict(java.util.TreeMap<LocalTime, LocalTime> sched, LocalTime start, LocalTime end) {
        var before = sched.floorEntry(start);
        if (before != null && before.getValue().isAfter(start))
            return true;
        var after = sched.ceilingEntry(start);
        if (after != null && end.isAfter(after.getKey()))
            return true;
        return false;
    }

    private static int enrollStudentIntoSchedule(int studentId, int scheduleId, Connection conn) throws SQLException {
        try (PreparedStatement insSe = conn.prepareStatement(
                "INSERT INTO student_exams (student_id, exam_schedule_id, status, is_paid) VALUES (?,?, 'Enrolled', 1)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            insSe.setInt(1, studentId);
            insSe.setInt(2, scheduleId);
            insSe.executeUpdate();
            try (ResultSet gk = insSe.getGeneratedKeys()) {
                gk.next();
                int registrationId = gk.getInt(1);

                // Manually decrease capacity since trigger doesn't exist
                try (PreparedStatement updateCap = conn.prepareStatement(
                        "UPDATE exam_schedules SET capacity = capacity - 1 WHERE id = ? AND capacity > 0")) {
                    updateCap.setInt(1, scheduleId);
                    updateCap.executeUpdate();
                }

                return registrationId;
            }
        }
    }

    private static int fetchRoomCapacity(String roomName, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT capacity FROM rooms WHERE room_name=?")) {
            ps.setString(1, roomName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        return 0;
    }

    private static boolean studentFullyBookedAllSlots(int studentId, LocalDate date, Connection conn,
            List<TimeSlotDef> slots)
            throws SQLException {
        // Quick heuristic: count distinct time_slot_id for that date for student
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(DISTINCT es.time_slot_id) FROM exam_schedules es JOIN student_exams se ON se.exam_schedule_id=es.id WHERE se.student_id=? AND es.scheduled_date=?")) {
            ps.setInt(1, studentId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    // If slots table empty we treat 4 default; else number of actual slots
                    int totalSlots = slots.isEmpty() ? 4 : slots.size();
                    return count >= totalSlots;
                }
            }
        }
        return false;
    }

    // Structure for loaded time slots
    private static class TimeSlotDef {
        int id;
        LocalTime start;
        LocalTime end;
    }

    private static List<TimeSlotDef> loadTimeSlots(Connection conn) throws SQLException {
        List<TimeSlotDef> list = new ArrayList<>();
        try (PreparedStatement ps = conn
                .prepareStatement("SELECT id, start_time FROM time_slots ORDER BY start_time")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Time st = rs.getTime("start_time");
                    if (st != null) {
                        TimeSlotDef def = new TimeSlotDef();
                        def.id = rs.getInt("id");
                        def.start = st.toLocalTime();
                        def.end = def.start.plusHours(2); // Default 2-hour slots
                        list.add(def);
                    }
                }
            }
        }
        if (list.isEmpty()) {
            // fallback canonical 4 slots (2h windows)
            for (int i = 0; i < 4; i++) {
                TimeSlotDef def = new TimeSlotDef();
                def.id = i + 1;
                def.start = LocalTime.of(9, 0).plusHours(2 * i);
                def.end = def.start.plusHours(2);
                list.add(def);
            }
        }
        return list;
    }

    private static List<String> loadRooms(Connection conn) throws SQLException {
        List<String> rooms = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT room_name FROM rooms ORDER BY capacity DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rooms.add(rs.getString(1));
                }
            }
        }
        if (rooms.isEmpty()) {
            // fallback to constant list
            for (String r : ROOMS)
                rooms.add(r);
        }
        return rooms;
    }

    private static boolean studentFullyBookedAllSlotsAligned(int studentId, LocalDate date, Connection conn)
            throws SQLException {
        // Check if student has any exam on this date
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM exam_schedules es JOIN student_exams se ON se.exam_schedule_id=es.id " +
                        "WHERE se.student_id=? AND es.scheduled_date=? LIMIT 1")) {
            ps.setInt(1, studentId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // if student has any exam this date, consider fully booked
            }
        }
    }

    private static List<RoomInfo> loadRoomsAligned(Connection conn) throws SQLException {
        List<RoomInfo> rooms = new ArrayList<>();
        try (PreparedStatement ps = conn
                .prepareStatement("SELECT id, room_name, capacity FROM rooms ORDER BY capacity DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RoomInfo room = new RoomInfo();
                    room.id = rs.getInt("id");
                    room.name = rs.getString("room_name");
                    room.capacity = rs.getInt("capacity");
                    rooms.add(room);
                }
            }
        }
        return rooms;
    }
}
