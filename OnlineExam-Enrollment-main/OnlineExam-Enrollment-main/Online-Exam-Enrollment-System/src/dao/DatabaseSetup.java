package dao;

import java.sql.*;

public class DatabaseSetup {

    public static void createTablesIfNotExist() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null) {
                createAdminsTable(conn);
                createRoomsTable(conn);
                createExamSchedulesTable(conn);
                createStudentExamsTable(conn);
                updateExamsTable(conn);
                insertSampleData(conn);
                System.out.println("✅ Database setup completed successfully!");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error setting up database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createAdminsTable(Connection conn) throws SQLException {
        String sql = """
                    CREATE TABLE IF NOT EXISTS admins (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(100) UNIQUE NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        role ENUM('admin', 'super_admin') DEFAULT 'admin',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("✅ Admins table created/verified");
        }
    }

    private static void createRoomsTable(Connection conn) throws SQLException {
        String sql = """
                    CREATE TABLE IF NOT EXISTS rooms (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        room_name VARCHAR(100) NOT NULL,
                        capacity INT NOT NULL DEFAULT 30,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("✅ Rooms table created/verified");
        }
    }

    private static void createExamSchedulesTable(Connection conn) throws SQLException {
        String sql = """
                    CREATE TABLE IF NOT EXISTS exam_schedules (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        exam_id INT NOT NULL,
                        room_id INT NOT NULL,
                        scheduled_date DATE NOT NULL,
                        scheduled_time TIME NOT NULL,
                        duration_minutes INT NOT NULL DEFAULT 120,
                        max_students INT NOT NULL DEFAULT 30,
                        status ENUM('Scheduled', 'In Progress', 'Completed', 'Cancelled') DEFAULT 'Scheduled',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
                        FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
                    )
                """;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("✅ Exam schedules table created/verified");
        }
    }

    private static void createStudentExamsTable(Connection conn) throws SQLException {
        String sql = """
                    CREATE TABLE IF NOT EXISTS student_exams (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        student_id INT NOT NULL,
                        exam_id INT NOT NULL,
                        status ENUM('Pending', 'Approved', 'Completed', 'Cancelled') DEFAULT 'Pending',
                        score INT DEFAULT NULL,
                        enrollment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
                        FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
                        UNIQUE KEY unique_enrollment (student_id, exam_id)
                    )
                """;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("✅ Student exams table created/verified");
        }
    }

    private static void updateExamsTable(Connection conn) throws SQLException {
        // Check if course_id column exists
        String checkSql = """
                    SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'exams'
                    AND COLUMN_NAME = 'course_id'
                """;

        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(checkSql);
            rs.next();
            int count = rs.getInt(1);

            if (count == 0) {
                // Add course_id column if it doesn't exist
                String alterSql = "ALTER TABLE exams ADD COLUMN course_id INT";
                stmt.executeUpdate(alterSql);
                System.out.println("✅ Added course_id column to exams table");
            }
        }
    }

    private static void insertSampleData(Connection conn) throws SQLException {
        // Insert sample rooms if table is empty
        String checkRooms = "SELECT COUNT(*) FROM rooms";
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(checkRooms);
            rs.next();
            int roomCount = rs.getInt(1);

            if (roomCount == 0) {
                String insertRooms = """
                            INSERT INTO rooms (room_name, capacity) VALUES
                            ('Computer Lab 1', 30),
                            ('Computer Lab 2', 25),
                            ('Lecture Hall A', 100),
                            ('Lecture Hall B', 80),
                            ('Conference Room 1', 20),
                            ('Conference Room 2', 15)
                        """;
                stmt.executeUpdate(insertRooms);
                System.out.println("✅ Sample rooms inserted");
            }
        }
    }

    public static void main(String[] args) {
        createTablesIfNotExist();
    }
}