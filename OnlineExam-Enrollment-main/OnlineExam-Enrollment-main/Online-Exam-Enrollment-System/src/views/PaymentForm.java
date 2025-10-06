package views;

import dao.DatabaseConnection;
import dao.ExamScheduleDAO;
import java.awt.*;
import java.sql.*;
import javax.swing.*;

public class PaymentForm extends JDialog {

    private final int studentId;
    private final int registrationId;
    private final int examId;
    private final String examName;
    private final double amount;
    private final boolean isPayAll; // flag to differentiate pay all vs single payment

    /**
     * Constructor for single exam payment
     */
    public PaymentForm(Frame owner, int studentId, int registrationId, int examId, String examName, double examFee) {
        super(owner, "Payment - " + examName, true);
        this.studentId = studentId;
        this.registrationId = registrationId;
        this.examId = examId;
        this.examName = examName;
        this.amount = examFee;
        this.isPayAll = false;
        initUI();
    }

    /**
     * Constructor for "Pay All" (multiple unpaid exams)
     */
    public PaymentForm(Frame owner, int studentId, int unpaidCount, double totalAmount) {
        super(owner, "Payment - All Unpaid Exams", true);
        this.studentId = studentId;
        this.registrationId = 0;
        this.examId = 0;
        this.examName = unpaidCount + " Unpaid Exam(s)";
        this.amount = totalAmount;
        this.isPayAll = true;
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
        JLabel lblTitle = new JLabel(isPayAll ? "Pay All Exams" : "Exam Payment");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 18));
        lblTitle.setForeground(new Color(30, 144, 255));
        main.add(lblTitle, gbc);

        // Exam info
        gbc.gridy = 1;
        JLabel lblExam = new JLabel(isPayAll ? "Total Exams: " + examName : "Exam: " + examName);
        lblExam.setFont(new Font("Arial", Font.PLAIN, 14));
        main.add(lblExam, gbc);

        gbc.gridy = 2;
        JLabel lblFee = new JLabel(String.format("Amount Due: ₱%.2f", amount));
        lblFee.setFont(new Font("Arial", Font.BOLD, 16));
        lblFee.setForeground(new Color(220, 53, 69));
        main.add(lblFee, gbc);

        // Amount field (read-only)
        gbc.gridwidth = 1;
        gbc.gridy = 3;
        gbc.gridx = 0;
        JLabel lblAmount = new JLabel("Payment Amount (₱):");
        main.add(lblAmount, gbc);

        gbc.gridx = 1;
        JTextField amountField = new JTextField(String.format("%.2f", amount));
        amountField.setEditable(false);
        amountField.setBackground(new Color(240, 240, 240));
        amountField.setFont(new Font("Arial", Font.BOLD, 14));
        main.add(amountField, gbc);

        // Payment method
        gbc.gridx = 0;
        gbc.gridy = 4;
        main.add(new JLabel("Payment Method:"), gbc);

        gbc.gridx = 1;
        JComboBox<String> methodBox = new JComboBox<>(
                new String[] { "GCash", "PayMaya", "Credit Card", "Bank Transfer", "Cash" });
        main.add(methodBox, gbc);

        // Reference number
        gbc.gridx = 0;
        gbc.gridy = 5;
        main.add(new JLabel("Reference Number:"), gbc);

        gbc.gridx = 1;
        JTextField refField = new JTextField();
        refField.setToolTipText("Enter transaction reference number");
        main.add(refField, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnPanel.setBackground(Color.WHITE);

        JButton btnConfirm = new JButton("Confirm Payment");
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
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        main.add(btnPanel, gbc);

        add(main, BorderLayout.CENTER);

        // Action listeners
        btnConfirm.addActionListener(e -> processPayment(methodBox, refField));
        btnCancel.addActionListener(e -> dispose());

        // Enter key submits
        refField.addActionListener(e -> processPayment(methodBox, refField));
    }

    private void processPayment(JComboBox<String> methodBox, JTextField refField) {
        String method = (String) methodBox.getSelectedItem();
        String ref = refField.getText().trim();

        // Validation
        if (ref.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a reference number.",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            refField.requestFocus();
            return;
        }

        if (ref.length() < 3) {
            JOptionPane.showMessageDialog(this,
                    "Reference number must be at least 3 characters.",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            refField.requestFocus();
            return;
        }

        // Confirmation
        int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Confirm payment of ₱%.2f via %s?\nRef: %s", amount, method, ref),
                "Confirm Payment",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Process payment
        if (isPayAll) {
            processPayAllExams(method, ref);
        } else {
            processSingleExam(method, ref);
        }
    }

    /**
     * Pay for all unpaid exams
     */
    private void processPayAllExams(String method, String ref) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) {
                JOptionPane.showMessageDialog(this,
                        "Cannot connect to database. Please check if MySQL is running.",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            conn.setAutoCommit(false);

            // Get all unpaid exam IDs
            PreparedStatement getUnpaid = conn.prepareStatement(
                    "SELECT id, exam_id FROM student_exams WHERE student_id = ? AND is_paid = 0");
            getUnpaid.setInt(1, studentId);
            ResultSet unpaidExams = getUnpaid.executeQuery();

            // Store exam IDs for scheduling
            java.util.List<Integer> examIds = new java.util.ArrayList<>();
            while (unpaidExams.next()) {
                examIds.add(unpaidExams.getInt("exam_id"));
            }

            if (examIds.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No unpaid exams found.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                conn.rollback();
                dispose();
                return;
            }

            // Record payment (exam_id = NULL for "pay all")
            PreparedStatement insertPayment = conn.prepareStatement(
                    "INSERT INTO payments (student_id, amount, payment_method, reference_no, exam_id) VALUES (?, ?, ?, ?, NULL)");
            insertPayment.setInt(1, studentId);
            insertPayment.setDouble(2, amount);
            insertPayment.setString(3, method);
            insertPayment.setString(4, ref);
            insertPayment.executeUpdate();

            // Mark all as paid
            PreparedStatement updateAll = conn.prepareStatement(
                    "UPDATE student_exams SET is_paid = 1 WHERE student_id = ? AND is_paid = 0");
            updateAll.setInt(1, studentId);
            int updated = updateAll.executeUpdate();

            conn.commit();

            // Create schedules for each exam (outside transaction)
            ExamScheduleDAO scheduleDAO = new ExamScheduleDAO();
            int schedulesCreated = 0;
            StringBuilder scheduleInfo = new StringBuilder();

            for (int eId : examIds) {
                boolean created = scheduleDAO.autoAssignSchedule(studentId, eId);
                if (created) {
                    schedulesCreated++;
                    String details = scheduleDAO.getScheduleDetails(studentId, eId);
                    if (details != null) {
                        scheduleInfo.append(details).append("\n\n");
                    }
                }
            }

            // Success message
            String message = String.format(
                    "✅ Payment Successful!\n\n" +
                            "Paid Exams: %d\n" +
                            "Schedules Created: %d\n" +
                            "Total Amount: ₱%.2f\n" +
                            "Method: %s\n" +
                            "Reference: %s",
                    updated, schedulesCreated, amount, method, ref);

            JOptionPane.showMessageDialog(this, message, "Payment Complete",
                    JOptionPane.INFORMATION_MESSAGE);

            dispose();

        } catch (SQLException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Database error: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
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

    /**
     * Pay for single exam
     */
    private void processSingleExam(String method, String ref) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) {
                JOptionPane.showMessageDialog(this,
                        "Cannot connect to database.",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            conn.setAutoCommit(false);

            // Record payment with exam_id
            PreparedStatement insertPayment = conn.prepareStatement(
                    "INSERT INTO payments (student_id, amount, payment_method, reference_no, exam_id) VALUES (?, ?, ?, ?, ?)");
            insertPayment.setInt(1, studentId);
            insertPayment.setDouble(2, amount);
            insertPayment.setString(3, method);
            insertPayment.setString(4, ref);
            insertPayment.setInt(5, examId);
            insertPayment.executeUpdate();

            // Mark exam as paid
            PreparedStatement updateExam = conn.prepareStatement(
                    "UPDATE student_exams SET is_paid = 1 WHERE id = ?");
            updateExam.setInt(1, registrationId);
            int updated = updateExam.executeUpdate();

            conn.commit();

            if (updated == 0) {
                JOptionPane.showMessageDialog(this,
                        "Failed to update payment status.",
                        "Warning",
                        JOptionPane.WARNING_MESSAGE);
            }

            // Create schedule
            ExamScheduleDAO scheduleDAO = new ExamScheduleDAO();
            boolean scheduleCreated = scheduleDAO.autoAssignSchedule(studentId, examId);

            String scheduleDetails = scheduleDAO.getScheduleDetails(studentId, examId);

            String message = String.format(
                    "✅ Payment Successful!\n\n" +
                            "Exam: %s\n" +
                            "Amount: ₱%.2f\n" +
                            "Method: %s\n" +
                            "Reference: %s\n\n" +
                            "%s",
                    examName, amount, method, ref,
                    scheduleCreated ? (scheduleDetails != null ? scheduleDetails : "Schedule assigned.") : "");

            JOptionPane.showMessageDialog(this, message, "Payment Complete",
                    JOptionPane.INFORMATION_MESSAGE);

            dispose();

        } catch (SQLException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Database error: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
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