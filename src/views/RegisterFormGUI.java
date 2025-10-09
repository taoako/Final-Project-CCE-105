//RegisterForm
package views;

import java.awt.*;
import java.sql.Date;
import javax.swing.*;

public class RegisterFormGUI extends JFrame {

    public RegisterFormGUI(int id, String name, Date date, String duration) {
        setTitle("Exam Details");
        setSize(400, 300);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(5, 1));

        add(new JLabel("Exam ID: " + id, SwingConstants.CENTER));
        add(new JLabel("Exam Name: " + name, SwingConstants.CENTER));
        add(new JLabel("Date: " + date, SwingConstants.CENTER));
        add(new JLabel("Duration: " + duration, SwingConstants.CENTER));

        JButton backButton = new JButton("Back");
        backButton.addActionListener(e -> dispose());
        add(backButton);

        setVisible(true);
    }
}
