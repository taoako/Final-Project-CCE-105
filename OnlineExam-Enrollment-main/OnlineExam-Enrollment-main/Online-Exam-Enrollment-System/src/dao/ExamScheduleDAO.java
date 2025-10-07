package dao;

import java.sql.*;
import java.util.Random;

public class ExamScheduleDAO {

    private final Connection conn;

    public ExamScheduleDAO() {
        conn = DatabaseConnection.getConnection();
    }

    public boolean autoAssignSchedule(int studentId, int examId) {
        try {
            // Dummy auto scheduling
            Random rand = new Random();
            String[] rooms = { "Room A", "Room B", "Room C" };
            String room = rooms[rand.nextInt(rooms.length)];
            Time time = Time.valueOf("09:00:00");
            Date date = new Date(System.currentTimeMillis() + (rand.nextInt(5) * 86400000L));

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO exam_schedules (student_id, exam_id, room_number, scheduled_date, scheduled_time) VALUES (?, ?, ?, ?, ?)");
            ps.setInt(1, studentId);
            ps.setInt(2, examId);
            ps.setString(3, room);
            ps.setDate(4, date);
            ps.setTime(5, time);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String getScheduleDetails(int studentId, int examId) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT room_number, scheduled_date, scheduled_time FROM exam_schedules WHERE student_id=? AND exam_id=? LIMIT 1");
            ps.setInt(1, studentId);
            ps.setInt(2, examId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return String.format("Exam scheduled at %s on %s, %s",
                        rs.getString("room_number"),
                        rs.getDate("scheduled_date"),
                        rs.getTime("scheduled_time"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
