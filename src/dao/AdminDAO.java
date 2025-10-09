package dao;

import models.Admin;
import java.sql.*;

public class AdminDAO {

    public Admin loginAdmin(String username, String password) {
        String sql = "SELECT * FROM admins WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Admin(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("role"));
            }

        } catch (SQLException e) {
            System.err.println("Error during admin login: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public boolean createAdmin(Admin admin) {
        String sql = "INSERT INTO admins (username, password, role) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, admin.getUsername());
            stmt.setString(2, admin.getPassword());
            stmt.setString(3, admin.getRole());

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            System.err.println("Error creating admin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateAdmin(Admin admin) {
        String sql = "UPDATE admins SET username = ?, password = ?, role = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, admin.getUsername());
            stmt.setString(2, admin.getPassword());
            stmt.setString(3, admin.getRole());
            stmt.setInt(4, admin.getId());

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            System.err.println("Error updating admin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteAdmin(int adminId) {
        String sql = "DELETE FROM admins WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, adminId);

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            System.err.println("Error deleting admin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Method to create admin table if it doesn't exist
    public void createAdminTableIfNotExists() {
        String sql = """
                    CREATE TABLE IF NOT EXISTS admins (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(100) UNIQUE NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        role ENUM('admin', 'super_admin') DEFAULT 'admin',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);

            // Create default admin if no admin exists
            String checkSql = "SELECT COUNT(*) FROM admins";
            ResultSet rs = stmt.executeQuery(checkSql);

            if (rs.next() && rs.getInt(1) == 0) {
                // Create default admin account
                Admin defaultAdmin = new Admin("admin", "admin123");
                defaultAdmin.setRole("super_admin");
                createAdmin(defaultAdmin);
                System.out.println("✅ Default admin account created: username=admin, password=admin123");
            }

        } catch (SQLException e) {
            System.err.println("Error creating admin table: " + e.getMessage());
            e.printStackTrace();
        }
    }
}