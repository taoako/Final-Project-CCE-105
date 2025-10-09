import javax.swing.SwingUtilities;

import dao.AdminDAO;
import dao.DatabaseSetup;
import views.LoginFormGUI;

public class Main {
    public static void main(String[] args) {
        // Measure total startup time
        long appStart = System.nanoTime();

        // Initialize database tables (measured)
        measure("DatabaseSetup.createTablesIfNotExist", () -> DatabaseSetup.createTablesIfNotExist());

        // Initialize admin table and create default admin account (measured)
        AdminDAO adminDAO = new AdminDAO();
        measure("AdminDAO.createAdminTableIfNotExists", () -> adminDAO.createAdminTableIfNotExists());

        // Launch UI
        SwingUtilities.invokeLater(() -> {
            long uiStart = System.nanoTime();
            LoginFormGUI login = new LoginFormGUI();
            login.setVisible(true);
            long uiEnd = System.nanoTime();
            System.out.printf("UI init took %.2f ms (%d ns)\n", (uiEnd - uiStart) / 1_000_000.0, (uiEnd - uiStart));
            // Also print using the requested format
            System.out.println("Execution time: " + (uiEnd - uiStart) + " nanoseconds (UI init)");
        });

        long appEnd = System.nanoTime();
        System.out.printf("Main startup path took %.2f ms (%d ns)\n", (appEnd - appStart) / 1_000_000.0,
                (appEnd - appStart));
        // Also print using the requested format
        System.out.println("Execution time: " + (appEnd - appStart) + " nanoseconds (startup)");
    }

    // Simple helper to measure a runnable block
    private static long measure(String label, Runnable action) {
        long start = System.nanoTime();
        try {
            action.run();
            return System.nanoTime() - start;
        } finally {
            long end = System.nanoTime();
            System.out.printf("%s took %.2f ms (%d ns)\n", label, (end - start) / 1_000_000.0, (end - start));
        }
    }
}
