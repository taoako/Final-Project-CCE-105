package dao;

import models.Student;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StudentDAO {

    // Ensure table exists (adds balance column if missing)
    public StudentDAO() {
        ensureTable();
    }

    private void ensureTable() {
        String create = """
                CREATE TABLE IF NOT EXISTS students (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(150) NOT NULL,
                    email VARCHAR(150) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    course_id INT,
                    balance DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        try (Connection conn = DatabaseConnection.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(create);
            // Add balance column if older table version
            try (ResultSet rs = st.executeQuery(
                    "SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='students' AND COLUMN_NAME='balance'")) {
                if (!rs.next()) {
                    st.executeUpdate("ALTER TABLE students ADD COLUMN balance DECIMAL(10,2) NOT NULL DEFAULT 0.00");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error ensuring students table: " + e.getMessage());
        }
    }

    public Student loginStudent(String emailOrUsername, String password) {
        // Accept either email or name as login handle
        String sql = "SELECT * FROM students WHERE (email = ? OR name = ?) AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, emailOrUsername);
            ps.setString(2, emailOrUsername);
            ps.setString(3, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Student(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getInt("course_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error logging in student: " + e.getMessage());
        }
        return null;
    }

    public boolean registerStudent(Student student) {
        String sql = "INSERT INTO students (name, email, password, course_id, balance) VALUES (?, ?, ?, ?, 0.00)";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getName());
            ps.setString(2, student.getEmail());
            ps.setString(3, student.getPassword());
            ps.setInt(4, student.getCourseId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
                System.err.println("Registration failed: email already exists");
            } else {
                System.err.println("Error registering student: " + e.getMessage());
            }
            return false;
        }
    }

    public List<Student> getAllStudents() {
        List<Student> list = new ArrayList<>();
        String sql = """
                SELECT s.id, s.name, s.email, s.password, s.course_id, s.balance,
                       COALESCE(c.name, 'No Course') as course_name
                FROM students s
                LEFT JOIN courses c ON s.course_id = c.id
                ORDER BY s.id
                """;
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Student student = new Student(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password"),
                        rs.getInt("course_id"),
                        rs.getDouble("balance"));
                student.setCourseName(rs.getString("course_name"));
                list.add(student);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching students: " + e.getMessage());
        }
        return list;
    }

    public Student getStudentById(int id) {
        String sql = """
                SELECT s.id, s.name, s.email, s.password, s.course_id, s.balance,
                       COALESCE(c.name, 'No Course') as course_name
                FROM students s
                LEFT JOIN courses c ON s.course_id = c.id
                WHERE s.id = ?
                """;
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student student = new Student(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getInt("course_id"),
                            rs.getDouble("balance"));
                    student.setCourseName(rs.getString("course_name"));
                    return student;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching student by ID: " + e.getMessage());
        }
        return null;
    }

    public boolean addStudent(Student student) {
        String sql = "INSERT INTO students (name, email, password, course_id, balance) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getName());
            ps.setString(2, student.getEmail());
            ps.setString(3, student.getPassword());
            ps.setInt(4, student.getCourseId());
            ps.setDouble(5, student.getBalance());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
                System.err.println("Add student failed: email already exists");
            } else {
                System.err.println("Error adding student: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean updateStudent(Student student) {
        String sql = "UPDATE students SET name = ?, email = ?, course_id = ?, balance = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getName());
            ps.setString(2, student.getEmail());
            ps.setInt(3, student.getCourseId());
            ps.setDouble(4, student.getBalance());
            ps.setInt(5, student.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating student: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteStudent(int id) {
        // First check if student has enrollments
        String checkSql = "SELECT COUNT(*) FROM student_exams WHERE student_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setInt(1, id);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    // Student has enrollments, delete them first
                    String deleteEnrollments = "DELETE FROM student_exams WHERE student_id = ?";
                    try (PreparedStatement deletePs = conn.prepareStatement(deleteEnrollments)) {
                        deletePs.setInt(1, id);
                        deletePs.executeUpdate();
                    }
                }
            }

            // Now delete the student
            String deleteSql = "DELETE FROM students WHERE id = ?";
            try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                deletePs.setInt(1, id);
                return deletePs.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error deleting student: " + e.getMessage());
            return false;
        }
    }

    public boolean updateBalance(int studentId, double delta) {
        String sql = "UPDATE students SET balance = balance + ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setInt(2, studentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating balance: " + e.getMessage());
            return false;
        }
    }

    public double getBalance(int studentId) {
        String sql = "SELECT balance FROM students WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getDouble(1);
            }
        } catch (SQLException e) {
            System.err.println("Error getting balance: " + e.getMessage());
        }
        return 0.0;
    }
}
