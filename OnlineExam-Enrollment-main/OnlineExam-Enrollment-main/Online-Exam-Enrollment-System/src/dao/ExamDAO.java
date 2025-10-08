package dao;

import models.Exam;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExamDAO {

    // Get all exams (for admin)
    public List<Exam> getAllExams() {
        List<Exam> exams = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM exams ORDER BY exam_name";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                exams.add(new Exam(
                        rs.getInt("id"),
                        rs.getString("exam_name"),
                        rs.getInt("course_id"),
                        rs.getString("duration")));
            }

        } catch (SQLException e) {
            System.err.println("Error fetching all exams: " + e.getMessage());
            e.printStackTrace();
        }
        return exams;
    }

    // Get exam by ID
    public Exam getExamById(int examId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM exams WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, examId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Exam(
                        rs.getInt("id"),
                        rs.getString("exam_name"),
                        rs.getInt("course_id"),
                        rs.getString("duration"));
            }

        } catch (SQLException e) {
            System.err.println("Error fetching exam by ID: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // Add new exam
    public boolean addExam(Exam exam) {
        String sql = "INSERT INTO exams (exam_name, course_id, duration) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, exam.getName());
            stmt.setInt(2, exam.getCourseId());
            stmt.setString(3, exam.getDuration());

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            System.err.println("Error adding exam: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Update exam
    public boolean updateExam(Exam exam) {
        String sql = "UPDATE exams SET exam_name = ?, course_id = ?, duration = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, exam.getName());
            stmt.setInt(2, exam.getCourseId());
            stmt.setString(3, exam.getDuration());
            stmt.setInt(4, exam.getId());

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            System.err.println("Error updating exam: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Delete exam
    public boolean deleteExam(int examId) {
        String sql = "DELETE FROM exams WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, examId);

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            System.err.println("Error deleting exam: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

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
                        rs.getInt("id"),
                        rs.getString("exam_name"),
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
                    "JOIN exams e ON se.exam_id = e.id " +
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
