import javax.swing.SwingUtilities;

import dao.AdminDAO;
import dao.DatabaseSetup;
import views.LoginFormGUI;

public class Main {
    public static void main(String[] args) {
        // Initialize database tables
        DatabaseSetup.createTablesIfNotExist();

        // Initialize admin table and create default admin account
        AdminDAO adminDAO = new AdminDAO();
        adminDAO.createAdminTableIfNotExists();

        SwingUtilities.invokeLater(() -> {
            LoginFormGUI login = new LoginFormGUI();
            login.setVisible(true);
        });
    }
}
