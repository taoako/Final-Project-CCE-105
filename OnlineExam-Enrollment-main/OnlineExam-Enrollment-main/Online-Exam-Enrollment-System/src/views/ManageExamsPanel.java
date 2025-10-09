package views;

import dao.DatabaseConnection;
import dao.SchedulingService; // SchedulingService uses TreeMap + PriorityQueue (see scheduleAndEnrollExam)
import java.awt.*;
import java.sql.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

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

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(Color.WHITE);
        topPanel.setBorder(new EmptyBorder(15, 20, 10, 20));

        JLabel title = new JLabel("Choose an Exam to Take:");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        topPanel.add(title, BorderLayout.WEST);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        searchPanel.setBackground(Color.WHITE);

        searchField = new JTextField(20);
        searchPanel.add(searchField);

        JButton btnSearch = new JButton("🔍");
        btnSearch.setFocusPainted(false);
        btnSearch.setBackground(new Color(245, 245, 245));
        btnSearch.addActionListener(this::handleSearchAction);
        searchPanel.add(btnSearch);

        topPanel.add(searchPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Center table
        examTable = new JTable() {
            // Tooltip support
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row > -1) {
                    int modelRow = convertRowIndexToModel(row);
                    Object statusObj = getModel().getValueAt(modelRow, 2);
                    String status = statusObj != null ? statusObj.toString() : "";
                    switch (status) {
                        case "Available":
                            return "Exam open for enrollment";
                        case "Enrolled":
                            return "You are already enrolled in this exam";
                        case "Full":
                        case "Unavailable":
                            return "Exam is full or no longer available";
                        case "Ongoing":
                            return "Exam is currently in progress";
                    }
                }
                return super.getToolTipText(e);
            }
        };

        examTable.setRowHeight(28);
        examTable.setFont(new Font("Arial", Font.PLAIN, 14));
        examTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Color-coded rows
        examTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    int modelRow = table.convertRowIndexToModel(row);
                    Object statusObj = table.getModel().getValueAt(modelRow, 2);
                    String status = statusObj != null ? statusObj.toString() : "";
                    switch (status) {
                        case "Available":
                            c.setBackground(new Color(204, 255, 204)); // light green
                            break;
                        case "Enrolled":
                            c.setBackground(new Color(204, 229, 255)); // light blue
                            break;
                        case "Full":
                        case "Unavailable":
                            c.setBackground(new Color(255, 204, 204)); // light red
                            break;
                        case "Ongoing":
                            c.setBackground(new Color(255, 255, 204)); // yellow
                            break;
                        default:
                            c.setBackground(Color.WHITE);
                            break;
                    }
                } else {
                    c.setBackground(new Color(153, 204, 255)); // highlight selected
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(examTable);
        scrollPane.setBorder(new EmptyBorder(10, 30, 10, 30));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(Color.WHITE);

        btnProceed = new JButton("Proceed");
        btnProceed.setBackground(new Color(30, 144, 255));
        btnProceed.setForeground(Color.WHITE);
        btnProceed.setFocusPainted(false);
        btnProceed.addActionListener(this::handleProceedAction);

        bottomPanel.add(btnProceed);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // adapter methods for method references
    private void handleSearchAction(java.awt.event.ActionEvent e) {
        searchExam();
    }

    private void handleProceedAction(java.awt.event.ActionEvent e) {
        proceedExam();
    }

    // ---------------- DATABASE LOGIC ----------------
    private void loadExams() {
        // Adapted to database WITHOUT exam_date column; sessions tracked in
        // exam_schedules.
        try {
            DefaultTableModel model = new DefaultTableModel(
                    new Object[] { "Exam ID", "Subject", "Status" }, 0);
            String sql = "SELECT e.id, e.exam_name AS subject, " +
                    "CASE WHEN EXISTS (SELECT 1 FROM student_exams se JOIN exam_schedules es ON se.exam_schedule_id=es.id WHERE se.student_id=? AND es.exam_id=e.id) THEN 'Enrolled' ELSE 'Available' END AS status "
                    +
                    "FROM exams e ORDER BY e.id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String subject = rs.getString("subject");
                        String status = rs.getString("status");
                        model.addRow(new Object[] { id, subject, status });
                    }
                }
            }
            examTable.setModel(model);
            examTable.removeColumn(examTable.getColumnModel().getColumn(0)); // hide id
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
            DefaultTableModel model = new DefaultTableModel(
                    new Object[] { "Exam ID", "Subject", "Status" }, 0);
            String sql = "SELECT e.id, e.exam_name AS subject, " +
                    "CASE WHEN EXISTS (SELECT 1 FROM student_exams se JOIN exam_schedules es ON se.exam_schedule_id=es.id WHERE se.student_id=? AND es.exam_id=e.id) THEN 'Enrolled' ELSE 'Available' END AS status "
                    +
                    "FROM exams e WHERE e.exam_name LIKE ? ORDER BY e.id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setString(2, "%" + keyword + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String subject = rs.getString("subject");
                        String status = rs.getString("status");
                        model.addRow(new Object[] { id, subject, status });
                    }
                }
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

        int modelRow = examTable.convertRowIndexToModel(row);
        DefaultTableModel model = (DefaultTableModel) examTable.getModel();
        int examId = Integer.parseInt(model.getValueAt(modelRow, 0).toString());
        String subject = model.getValueAt(modelRow, 1).toString();
        String status = model.getValueAt(modelRow, 2).toString();

        if ("Enrolled".equals(status)) {
            JOptionPane.showMessageDialog(this, "⚠️ You are already enrolled in this exam.", "Already Enrolled",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!"Available".equals(status)) {
            JOptionPane.showMessageDialog(this, "This exam is not available right now.", "Unavailable",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            PreparedStatement ps = conn.prepareStatement("SELECT balance FROM students WHERE id = ?");
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                double balance = rs.getDouble("balance");
                if (balance < EXAM_FEE) {
                    JOptionPane.showMessageDialog(this,
                            "❌ Insufficient balance. You need ₱" + (EXAM_FEE - balance) + " more.",
                            "Not Enough Balance", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                int confirm = JOptionPane.showConfirmDialog(this,
                        "Pay ₱" + EXAM_FEE + " for " + subject + " exam?\nYour balance: ₱" + balance,
                        "Confirm Enrollment", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    enrollAndSchedule(examId, subject);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void enrollAndSchedule(int examId, String subject) {
        try {
            conn.setAutoCommit(false);
            // Deduct fee
            PreparedStatement updBal = conn.prepareStatement("UPDATE students SET balance = balance - ? WHERE id = ?");
            updBal.setDouble(1, EXAM_FEE);
            updBal.setInt(2, studentId);
            updBal.executeUpdate();
            // Use SchedulingService to REUSE or CREATE schedule then enroll (TreeMap +
            // PriorityQueue inside)
            SchedulingService.AssignmentResult ar = SchedulingService.scheduleAndEnrollExam(studentId, examId, conn);
            if (ar == null)
                throw new SQLException("Scheduling failed");
            JOptionPane.showMessageDialog(this,
                    "✅ Enrollment successful!\n\nExam: " + subject +
                            "\nScheduled Date: " + ar.date +
                            "\nStart Time: " + ar.start +
                            "\nRoom: " + ar.room +
                            "\nSchedule ID: " + ar.examScheduleId,
                    "Exam Scheduled", JOptionPane.INFORMATION_MESSAGE);
            conn.commit();
            loadExams(); // refresh UI

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            JOptionPane.showMessageDialog(this, "Error during enrollment: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    // Removed legacy assignSchedule – logic migrated to
    // SchedulingService.scheduleAndEnrollExam

    private int getSelectedExamId(int visibleRowIndex) {
        int modelIndex = examTable.convertRowIndexToModel(visibleRowIndex);
        Object idValue = ((DefaultTableModel) examTable.getModel()).getValueAt(modelIndex, 0);
        return Integer.parseInt(idValue.toString());
    }
}
