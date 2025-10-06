package dao;

import models.Exam;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExamDAO {

    // Get available exams for student's course
    public List<Exam> getAvailableExams(int courseId) {
        List<Exam> exams = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM exams WHERE course_id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                exams.add(new Exam(
                        rs.getInt("exam_id"),
                        rs.getString("exam_name"),
                        rs.getDate("exam_date"),
                        rs.getString("duration")));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return exams;
    }

    // Register student for an exam
    public boolean registerExam(int studentId, int examId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO student_exams (student_id, exam_id, status) VALUES (?, ?, 'Pending')";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, studentId);
            stmt.setInt(2, examId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Get student's exams with status
    public List<String> getStudentExamHistory(int studentId) {
        List<String> history = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT e.exam_name, e.exam_date, se.status, se.score " +
                    "FROM student_exams se " +
                    "JOIN exams e ON se.exam_id = e.exam_id " +
                    "WHERE se.student_id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, studentId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String record = rs.getString("exam_name") + " | " +
                        rs.getDate("exam_date") + " | " +
                        rs.getString("status") + " | Score: " +
                        (rs.getObject("score") != null ? rs.getInt("score") : "N/A");
                history.add(record);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }
}
