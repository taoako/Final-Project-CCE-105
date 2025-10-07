package views;

import dao.DatabaseConnection;
import java.awt.*;
import java.sql.*;
import javax.swing.*;

public class PaymentForm extends JDialog {

    private final int studentId;
    private final double amount;
    private final String studentName;

    public PaymentForm(Window owner, int studentId, String studentName, double defaultAmount) {
        super(owner, "Cash-In to Wallet");
        this.studentId = studentId;
        this.studentName = studentName;
        this.amount = defaultAmount;
        initUI();
    }

    private void initUI() {
        setSize(450, 380);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        main.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel lblTitle = new JLabel("💵 Cash-In to Wallet");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 20));
        lblTitle.setForeground(new Color(30, 144, 255));
        main.add(lblTitle, gbc);

        // Student Info
        gbc.gridy = 1;
        JLabel lblName = new JLabel("Student: " + studentName);
        lblName.setFont(new Font("Arial", Font.PLAIN, 14));
        main.add(lblName, gbc);

        // Amount field
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        JLabel lblAmount = new JLabel("Cash-In Amount (₱):");
        main.add(lblAmount, gbc);

        gbc.gridx = 1;
        JTextField amountField = new JTextField(String.format("%.2f", amount));
        amountField.setFont(new Font("Arial", Font.BOLD, 14));
        main.add(amountField, gbc);

        // Payment method
        gbc.gridx = 0;
        gbc.gridy = 3;
        main.add(new JLabel("Payment Method:"), gbc);

        gbc.gridx = 1;
        JComboBox<String> methodBox = new JComboBox<>(
                new String[] { "GCash", "PayMaya", "Credit Card", "Bank Transfer", "Cash" });
        main.add(methodBox, gbc);

        // Reference number
        gbc.gridx = 0;
        gbc.gridy = 4;
        main.add(new JLabel("Reference Number:"), gbc);

        gbc.gridx = 1;
        JTextField refField = new JTextField();
        refField.setToolTipText("Enter transaction reference number");
        main.add(refField, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnPanel.setBackground(Color.WHITE);

        JButton btnConfirm = new JButton("Confirm Cash-In");
        btnConfirm.setBackground(new Color(40, 167, 69));
        btnConfirm.setForeground(Color.WHITE);
        btnConfirm.setFocusPainted(false);
        btnConfirm.setFont(new Font("Arial", Font.BOLD, 13));
        btnConfirm.setPreferredSize(new Dimension(160, 35));

        JButton btnCancel = new JButton("Cancel");
        btnCancel.setBackground(new Color(108, 117, 125));
        btnCancel.setForeground(Color.WHITE);
        btnCancel.setFocusPainted(false);
        btnCancel.setFont(new Font("Arial", Font.BOLD, 13));
        btnCancel.setPreferredSize(new Dimension(100, 35));

        btnPanel.add(btnConfirm);
        btnPanel.add(btnCancel);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        main.add(btnPanel, gbc);

        add(main, BorderLayout.CENTER);

        // Action listeners
        btnConfirm.addActionListener(e -> processCashIn(methodBox, refField, amountField));
        btnCancel.addActionListener(e -> dispose());
    }

    private void processCashIn(JComboBox<String> methodBox, JTextField refField, JTextField amountField) {
        String method = (String) methodBox.getSelectedItem();
        String ref = refField.getText().trim();
        String amtStr = amountField.getText().trim();

        if (ref.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a reference number.",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        double cashInAmount;
        try {
            cashInAmount = Double.parseDouble(amtStr);
            if (cashInAmount <= 0) {
                JOptionPane.showMessageDialog(this, "Amount must be greater than zero.", "Invalid Input",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid amount format.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Confirm cash-in of ₱%.2f via %s?\nRef: %s", cashInAmount, method, ref),
                "Confirm Cash-In",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION)
            return;

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) {
                JOptionPane.showMessageDialog(this,
                        "Cannot connect to database. Please check your connection.",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            conn.setAutoCommit(false);

            // Update student balance
            PreparedStatement updateBal = conn.prepareStatement(
                    "UPDATE students SET balance = balance + ? WHERE id = ?");
            updateBal.setDouble(1, cashInAmount);
            updateBal.setInt(2, studentId);
            updateBal.executeUpdate();

            // Optional: record payment transaction
            PreparedStatement insertPayment = conn.prepareStatement(
                    "INSERT INTO payments (student_id, amount, payment_method, reference_no) VALUES (?, ?, ?, ?)");
            insertPayment.setInt(1, studentId);
            insertPayment.setDouble(2, cashInAmount);
            insertPayment.setString(3, method);
            insertPayment.setString(4, ref);
            insertPayment.executeUpdate();

            conn.commit();

            JOptionPane.showMessageDialog(this,
                    String.format("✅ Cash-In Successful!\n\nAmount Added: ₱%.2f\nMethod: %s\nReference: %s",
                            cashInAmount, method, ref),
                    "Success", JOptionPane.INFORMATION_MESSAGE);

            dispose();

        } catch (SQLException ex) {
            ex.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            JOptionPane.showMessageDialog(this,
                    "Database error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }
}
