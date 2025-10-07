import javax.swing.SwingUtilities;

import views.LoginFormGUI;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginFormGUI login = new LoginFormGUI();
            login.setVisible(true);
        });
    }
}
