//ExamSchedule
package dao;

import java.sql.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ExamScheduleDAO {
    
    private static final Statement DatabaseConnection = null;

    // AUTO-ASSIGN: System automatically assigns student to available schedule
    public boolean autoAssignSchedule(int studentId, int examId) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Check if schedule already exists for this exam
            String checkExisting = "SELECT schedule_id FROM exam_schedules WHERE student_id = ? AND exam_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkExisting);
            checkStmt.setInt(1, studentId);
            checkStmt.setInt(2, examId);
            ResultSet existingRs = checkStmt.executeQuery();
            
            if (existingRs.next()) {
                conn.rollback();
                return true; // Already has schedule
            }
            
            // Get exam details
            String examQuery = "SELECT exam_date, duration FROM exams WHERE id = ?";
            PreparedStatement examStmt = conn.prepareStatement(examQuery);
            examStmt.setInt(1, examId);
            ResultSet examRs = examStmt.executeQuery();
            
            if (!examRs.next()) {
                conn.rollback();
                return false;
            }
            
            Date examDate = examRs.getDate("exam_date");
            String duration = examRs.getString("duration");
            
            // Generate schedule time (9 AM default, can be randomized)
            LocalTime scheduleTime = LocalTime.of(9, 0);
            
            // Assign room (simple round-robin or random)
            String roomQuery = "SELECT room_number FROM exam_schedules GROUP BY room_number ORDER BY COUNT(*) LIMIT 1";
            PreparedStatement roomStmt = conn.prepareStatement(roomQuery);
            ResultSet roomRs = roomStmt.executeQuery();
            
            String roomNumber = "Room 101"; // Default
            if (roomRs.next()) {
                roomNumber = roomRs.getString("room_number");
            } else {
                // If no existing schedules, use default rooms rotating
                String[] rooms = {"Room 101", "Room 102", "Room 103", "Computer Lab 1", "Computer Lab 2"};
                roomNumber = rooms[(int)(Math.random() * rooms.length)];
            }
            
            // Insert schedule
            String insertSchedule = "INSERT INTO exam_schedules (student_id, exam_id, scheduled_date, scheduled_time, room_number, status) " +
                                  "VALUES (?, ?, ?, ?, ?, 'scheduled')";
            PreparedStatement insertStmt = conn.prepareStatement(insertSchedule);
            insertStmt.setInt(1, studentId);
            insertStmt.setInt(2, examId);
            insertStmt.setDate(3, examDate);
            insertStmt.setTime(4, Time.valueOf(scheduleTime));
            insertStmt.setString(5, roomNumber);
            
            int result = insertStmt.executeUpdate();
            
            conn.commit();
            return result > 0;
            
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try { 
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }
    
    // Get schedule details for a student's exam
    public String getScheduleDetails(int studentId, int examId) {
        String query = "SELECT es.scheduled_date, es.scheduled_time, es.room_number, e.exam_name, e.duration " +
                      "FROM exam_schedules es " +
                      "JOIN exams e ON es.exam_id = e.id " +
                      "WHERE es.student_id = ? AND es.exam_id = ? AND es.status = 'scheduled'";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, studentId);
            stmt.setInt(2, examId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return String.format(
                    "📋 Exam: %s\n" +
                    "📅 Date: %s\n" +
                    "⏰ Time: %s\n" +
                    "🏢 Room: %s\n" +
                    "⏱️ Duration: %s",
                    rs.getString("exam_name"),
                    rs.getDate("scheduled_date"),
                    rs.getTime("scheduled_time"),
                    rs.getString("room_number"),
                    rs.getString("duration")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    // Get all schedules for a student
    public List<String> getStudentSchedules(int studentId) {
        List<String> schedules = new ArrayList<>();
        String query = "SELECT e.exam_name, es.scheduled_date, es.scheduled_time, es.room_number " +
                      "FROM exam_schedules es " +
                      "JOIN exams e ON es.exam_id = e.id " +
                      "WHERE es.student_id = ? AND es.status = 'scheduled' " +
                      "ORDER BY es.scheduled_date, es.scheduled_time";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, studentId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String schedule = String.format("%s | %s %s | %s",
                    rs.getString("exam_name"),
                    rs.getDate("scheduled_date"),
                    rs.getTime("scheduled_time"),
                    rs.getString("room_number")
                );
                schedules.add(schedule);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return schedules;
    }
}