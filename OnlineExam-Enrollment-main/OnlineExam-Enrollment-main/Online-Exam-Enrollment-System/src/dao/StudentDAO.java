package dao;

import java.sql.*;
import models.Student;

public class StudentDAO {

    private static final Statement DatabaseConnection = null;

    // ✅ Login existing student
    public Student loginStudent(String email, String password) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("❌ Database connection is null!");
                return null;
            }

            // Make email case-insensitive
            String sql = "SELECT * FROM students WHERE LOWER(email) = LOWER(?) AND password = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // ✅ FIXED: Use "id" instead of "student_id"
                return new Student(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password"),
                        rs.getInt("course_id"));
            } else {
                System.out.println("⚠️ No student found for email: " + email);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ✅ Register new student (unchanged)
    public boolean registerStudent(Student student) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO students (name, email, password, course_id) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, student.getName());
            stmt.setString(2, student.getEmail());
            stmt.setString(3, student.getPassword());
            stmt.setInt(4, student.getCourseId());

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
