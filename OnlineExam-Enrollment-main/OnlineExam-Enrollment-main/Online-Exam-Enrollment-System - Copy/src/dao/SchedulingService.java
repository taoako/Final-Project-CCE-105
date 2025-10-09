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

    /**
     * Intelligent single-row scheduling using TreeMap + PriorityQueue with room
     * load balancing.
     * Uses O(log n) neighbor lookups to detect conflicts quickly.
     * 
     * @param studentExamId id of student_exams row
     * @param externalConn  optional existing connection (not closed if provided)
     * @return true if scheduled (or already scheduled)
     */
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
        LocalDate today = LocalDate.now();
        int durationMin = fetchExamDurationMinutes(examId, conn);

        // STEP 1: Try reuse existing schedule (capacity check)
        Integer reuseId = null;
        LocalTime reuseStart = null;
        String reuseRoom = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT es.id, es.room_number, es.scheduled_time, r.capacity, " +
                        "(SELECT COUNT(*) FROM student_exams se WHERE se.exam_schedule_id=es.id) AS enrolled " +
                        "FROM exam_schedules es JOIN rooms r ON r.room_name = es.room_number " +
                        "WHERE es.exam_id=? AND es.scheduled_date=? ORDER BY es.scheduled_time")) {
            ps.setInt(1, examId);
            ps.setDate(2, java.sql.Date.valueOf(today));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cap = rs.getInt("capacity");
                    int enrolled = rs.getInt("enrolled");
                    if (enrolled < cap) {
                        reuseId = rs.getInt("id");
                        reuseRoom = rs.getString("room_number");
                        Time t = rs.getTime("scheduled_time");
                        if (t != null)
                            reuseStart = t.toLocalTime();
                        break;
                    }
                }
            }
        }
        int scheduleId;
        LocalTime start;
        String room;
        if (reuseId != null) {
            scheduleId = reuseId;
            start = reuseStart != null ? reuseStart : LocalTime.of(9, 0);
            room = reuseRoom;
        } else {
            // STEP 2: Build occupancy map (TreeMap per room) + usage counts
            Map<String, java.util.TreeMap<LocalTime, LocalTime>> roomSchedules = new HashMap<>();
            Map<String, Integer> usage = new HashMap<>();
            List<String> rooms = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT room_name, capacity FROM rooms")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rn = rs.getString(1);
                        rooms.add(rn);
                        roomSchedules.put(rn, new java.util.TreeMap<>());
                        usage.put(rn, 0);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT es.room_number, es.scheduled_time, e.duration FROM exam_schedules es JOIN exams e ON e.id=es.exam_id WHERE es.scheduled_date=?")) {
                ps.setDate(1, java.sql.Date.valueOf(today));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rn = rs.getString(1);
                        Time st = rs.getTime(2);
                        String dur = rs.getString(3);
                        int dMin = parseDurationMinutes(dur != null ? dur : "2 hours");
                        if (rn != null && st != null) {
                            LocalTime s = st.toLocalTime();
                            roomSchedules.get(rn).put(s, s.plusMinutes(dMin));
                            usage.put(rn, usage.get(rn) + 1);
                        }
                    }
                }
            }
            // Candidate slot generation (PriorityQueue ordering earliest start then lower
            // usage)
            java.util.PriorityQueue<CandidateSlot> pq = new java.util.PriorityQueue<>(Comparator
                    .comparing((CandidateSlot c) -> c.start)
                    .thenComparingInt(c -> c.roomUsage)
                    .thenComparing(c -> c.room));
            // time slots from table
            List<LocalTime> starts = new ArrayList<>();
            try (PreparedStatement ps = conn
                    .prepareStatement("SELECT start_time FROM time_slots ORDER BY start_time")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Time t = rs.getTime(1);
                        if (t != null)
                            starts.add(t.toLocalTime());
                    }
                }
            }
            if (starts.isEmpty()) {
                starts.add(LocalTime.of(9, 0));
                starts.add(LocalTime.of(11, 0));
                starts.add(LocalTime.of(13, 0));
                starts.add(LocalTime.of(15, 0));
            }
            for (LocalTime st : starts) {
                LocalTime end = st.plusMinutes(durationMin);
                for (String r : rooms) {
                    java.util.TreeMap<LocalTime, LocalTime> sched = roomSchedules.get(r);
                    boolean conflict = false;
                    if (!sched.isEmpty()) {
                        var before = sched.floorEntry(st);
                        var after = sched.ceilingEntry(st);
                        if (before != null && before.getValue().isAfter(st))
                            conflict = true;
                        if (!conflict && after != null && end.isAfter(after.getKey()))
                            conflict = true;
                    }
                    if (!conflict) {
                        CandidateSlot cs = new CandidateSlot();
                        cs.start = st;
                        cs.end = end;
                        cs.room = r;
                        cs.roomUsage = usage.get(r);
                        pq.add(cs);
                    }
                }
            }
            if (pq.isEmpty()) {
                CandidateSlot cs = new CandidateSlot();
                cs.start = starts.get(0);
                cs.end = cs.start.plusMinutes(durationMin);
                cs.room = rooms.get(0);
                cs.roomUsage = 0;
                pq.add(cs);
            }
            CandidateSlot chosen = pq.poll();
            start = chosen.start;
            room = chosen.room;
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO exam_schedules (student_id, exam_id, room_number, scheduled_date, scheduled_time) VALUES (?,?,?,?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ins.setInt(1, studentId);
                ins.setInt(2, examId);
                ins.setString(3, room);
                ins.setDate(4, java.sql.Date.valueOf(today));
                ins.setTime(5, Time.valueOf(start));
                ins.executeUpdate();
                try (ResultSet gk = ins.getGeneratedKeys()) {
                    gk.next();
                    scheduleId = gk.getInt(1);
                }
            }
        }
        // STEP 4: Enroll student pointing to schedule (no duplicate enrollment check
        // here)
        int registrationId;
        try (PreparedStatement insSe = conn.prepareStatement(
                "INSERT INTO student_exams (student_id, exam_schedule_id, status, is_paid) VALUES (?,?, 'Enrolled', 1)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            insSe.setInt(1, studentId);
            insSe.setInt(2, scheduleId);
            insSe.executeUpdate();
            try (ResultSet gk = insSe.getGeneratedKeys()) {
                gk.next();
                registrationId = gk.getInt(1);
            }
        }
        AssignmentResult ar = new AssignmentResult();
        ar.registrationId = registrationId;
        ar.examScheduleId = scheduleId;
        ar.date = today;
        ar.start = start;
        ar.room = room;
        return ar;
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
}
