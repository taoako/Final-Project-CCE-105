//ManagementExam
package views;

import dao.DatabaseConnection;
import java.awt.*;
import java.sql.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class ManageExamsPanel extends JPanel {

    private final int studentId;
    private Connection conn;
    private JTable examTable;
    private JTextField searchField;
    private JButton btnProceed;
    private static final int EXAM_FEE = 150;

    public ManageExamsPanel(int studentId) {
        this.studentId = studentId;
        conn = DatabaseConnection.getConnection();
        initUI();
        loadExams();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // Title bar
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(Color.WHITE);
        topPanel.setBorder(new EmptyBorder(15, 20, 10, 20));
        JLabel title = new JLabel("Choose an Exam to take:");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        topPanel.add(title, BorderLayout.WEST);

        // Search + filter
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        searchPanel.setBackground(Color.WHITE);

        searchField = new JTextField(20);
        searchField.putClientProperty("JTextField.placeholderText", "Search Exam");
        searchPanel.add(searchField);

        JButton btnSearch = new JButton("🔍");
        btnSearch.setFocusPainted(false);
        btnSearch.setBackground(new Color(245, 245, 245));
        btnSearch.addActionListener(e -> searchExam());
        searchPanel.add(btnSearch);

        topPanel.add(searchPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Center area for table
        examTable = new JTable();
        examTable.setRowHeight(28);
        examTable.setFont(new Font("Arial", Font.PLAIN, 14));
        examTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(examTable);
        scrollPane.setBorder(new EmptyBorder(10, 30, 10, 30));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom button area
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(Color.WHITE);

        btnProceed = new JButton("Proceed");
        btnProceed.setBackground(new Color(30, 144, 255));
        btnProceed.setForeground(Color.WHITE);
        btnProceed.setFocusPainted(false);
        btnProceed.addActionListener(e -> proceedExam());

        bottomPanel.add(btnProceed);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ---------------- DATABASE LOGIC ----------------
    private void loadExams() {
        try {
            DefaultTableModel model = new DefaultTableModel(new Object[] { "Exam ID", "Subject", "Status" }, 0);
            String sql = """
                    SELECT e.id, e.exam_name AS subject,
                    CASE
                        WHEN e.exam_date > CURDATE() THEN 'Available'
                        WHEN e.exam_date = CURDATE() THEN 'Ongoing'
                        ELSE 'Unavailable'
                    END AS status
                    FROM exams e
                    JOIN students s ON e.course_id = s.course_id
                    WHERE s.id = ?
                    ORDER BY e.exam_date
                    """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String subject = rs.getString("subject");
                String status = rs.getString("status");
                model.addRow(new Object[] { id, subject, status });
            }
            examTable.setModel(model);
            examTable.removeColumn(examTable.getColumnModel().getColumn(0)); // hide ID column
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void searchExam() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            loadExams();
            return;
        }
        try {
            DefaultTableModel model = new DefaultTableModel(new Object[] { "Exam ID", "Subject", "Status" }, 0);
            String sql = """
                    SELECT e.id, e.exam_name AS subject,
                    CASE
                        WHEN e.exam_date > CURDATE() THEN 'Available'
                        WHEN e.exam_date = CURDATE() THEN 'Ongoing'
                        ELSE 'Unavailable'
                    END AS status
                    FROM exams e
                    JOIN students s ON e.course_id = s.course_id
                    WHERE s.id = ? AND e.exam_name LIKE ?
                    ORDER BY e.exam_date
                    """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ps.setString(2, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String subject = rs.getString("subject");
                String status = rs.getString("status");
                model.addRow(new Object[] { id, subject, status });
            }
            examTable.setModel(model);
            examTable.removeColumn(examTable.getColumnModel().getColumn(0));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void proceedExam() {
        int row = examTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select an exam first.", "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int examId = getSelectedExamId(row);
        String subject = (String) examTable.getValueAt(row, 0);
        String status = (String) examTable.getValueAt(row, 1);

        if (!"Available".equals(status)) {
            JOptionPane.showMessageDialog(this, "This exam is not available right now.", "Unavailable",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // check balance first
            PreparedStatement balanceCheck = conn.prepareStatement("SELECT balance FROM students WHERE id = ?");
            balanceCheck.setInt(1, studentId);
            ResultSet balRs = balanceCheck.executeQuery();
            if (balRs.next()) {
                double balance = balRs.getDouble("balance");
                if (balance < EXAM_FEE) {
                    JOptionPane.showMessageDialog(this,
                            "❌ Insufficient balance. Please add at least ₱" + (EXAM_FEE - balance) + " more.",
                            "Not Enough Balance", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            // Deduct from balance and register exam (transaction)
            try {
                conn.setAutoCommit(false);
                PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO student_exams (student_id, exam_id, status, is_paid) VALUES (?, ?, 'Pending', 1)");
                ins.setInt(1, studentId);
                ins.setInt(2, examId);
                ins.executeUpdate();

                PreparedStatement upd = conn.prepareStatement("UPDATE students SET balance = balance - ? WHERE id = ?");
                upd.setDouble(1, EXAM_FEE);
                upd.setInt(2, studentId);
                upd.executeUpdate();

                conn.commit();

                JOptionPane.showMessageDialog(this,
                        "Exam registered and ₱" + EXAM_FEE + " deducted from your balance.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);

                // Paid -> proceed to schedule
                int confirm = JOptionPane.showConfirmDialog(this, "Proceed to take " + subject + " exam?", "Confirm",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    JOptionPane.showMessageDialog(this, "Exam scheduled automatically!", "Scheduled",
                            JOptionPane.INFORMATION_MESSAGE);
                    // TODO: actual scheduling logic (auto date/time allocation)
                }
            } catch (SQLException transEx) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    rbEx.printStackTrace();
                }
                transEx.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error processing registration: " + transEx.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException acEx) {
                    acEx.printStackTrace();
                }
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private int getSelectedExamId(int visibleRowIndex) {
        int modelIndex = examTable.convertRowIndexToModel(visibleRowIndex);
        Object idValue = ((DefaultTableModel) examTable.getModel()).getValueAt(modelIndex, 0);
        return Integer.parseInt(idValue.toString());
    }
}
