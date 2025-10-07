package views;

import dao.DatabaseConnection;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
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
        btnSearch.addActionListener(e -> searchExam());
        searchPanel.add(btnSearch);

        topPanel.add(searchPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Center table
        examTable = new JTable();
        examTable.setRowHeight(28);
        examTable.setFont(new Font("Arial", Font.PLAIN, 14));
        examTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

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
        btnProceed.addActionListener(e -> proceedExam());

        bottomPanel.add(btnProceed);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ---------------- DATABASE LOGIC ----------------
    private void loadExams() {
        try {
            final int CAPACITY = 30; // default capacity since schema lacks max_students column
            DefaultTableModel model = new DefaultTableModel(
                    new Object[] { "Exam ID", "Subject", "Date", "Slots", "Status" }, 0);
            String sql = "SELECT e.id, e.exam_name AS subject, e.exam_date, COUNT(se.id) AS enrolled, " +
                    "CASE WHEN e.exam_date > CURDATE() THEN 'Available' " +
                    "WHEN e.exam_date = CURDATE() THEN 'Ongoing' ELSE 'Unavailable' END AS status " +
                    "FROM exams e LEFT JOIN student_exams se ON e.id = se.exam_id " +
                    "JOIN students s ON e.course_id = s.course_id WHERE s.id=? GROUP BY e.id ORDER BY e.exam_date";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String subject = rs.getString("subject");
                Date date = rs.getDate("exam_date");
                int enrolled = rs.getInt("enrolled");
                String status = rs.getString("status");
                String slots = enrolled + "/" + CAPACITY;
                model.addRow(new Object[] { id, subject, date, slots, status });
            }

            examTable.setModel(model);
            examTable.removeColumn(examTable.getColumnModel().getColumn(0)); // hide ID
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
            final int CAPACITY = 30;
            DefaultTableModel model = new DefaultTableModel(
                    new Object[] { "Exam ID", "Subject", "Date", "Slots", "Status" }, 0);
            String sql = "SELECT e.id, e.exam_name AS subject, e.exam_date, COUNT(se.id) AS enrolled, " +
                    "CASE WHEN e.exam_date > CURDATE() THEN 'Available' " +
                    "WHEN e.exam_date = CURDATE() THEN 'Ongoing' ELSE 'Unavailable' END AS status " +
                    "FROM exams e LEFT JOIN student_exams se ON e.id = se.exam_id " +
                    "JOIN students s ON e.course_id = s.course_id WHERE s.id=? AND e.exam_name LIKE ? GROUP BY e.id ORDER BY e.exam_date";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ps.setString(2, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String subject = rs.getString("subject");
                Date date = rs.getDate("exam_date");
                int enrolled = rs.getInt("enrolled");
                String status = rs.getString("status");
                String slots = enrolled + "/" + CAPACITY;
                model.addRow(new Object[] { id, subject, date, slots, status });
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
        String status = (String) examTable.getValueAt(row, 3);

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

                // ✅ Check if exam has reached max capacity
                PreparedStatement limitCheck = conn.prepareStatement("""
                        SELECT COUNT(se.id) AS enrolled, e.max_students
                        FROM exams e
                        LEFT JOIN student_exams se ON e.id = se.exam_id
                        WHERE e.id = ?
                        GROUP BY e.max_students
                        """);
                limitCheck.setInt(1, examId);
                ResultSet rsLimit = limitCheck.executeQuery();

                if (rsLimit.next()) {
                    int enrolled = rsLimit.getInt("enrolled");
                    int max = rsLimit.getInt("max_students");
                    if (enrolled >= max) {
                        JOptionPane.showMessageDialog(this,
                                "❌ Exam is full! (" + max + " students max)",
                                "Exam Full",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
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

            // Deduct balance
            PreparedStatement updBal = conn.prepareStatement("UPDATE students SET balance = balance - ? WHERE id = ?");
            updBal.setDouble(1, EXAM_FEE);
            updBal.setInt(2, studentId);
            updBal.executeUpdate();

            // Enroll student
            PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO student_exams (student_id, exam_id, status, is_paid) VALUES (?, ?, 'Enrolled', 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ins.setInt(1, studentId);
            ins.setInt(2, examId);
            ins.executeUpdate();

            ResultSet genKeys = ins.getGeneratedKeys();
            int registrationId = 0;
            if (genKeys.next())
                registrationId = genKeys.getInt(1);

            // Assign schedule
            assignSchedule(registrationId, examId);

            conn.commit();

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

    private void assignSchedule(int registrationId, int examId) throws SQLException {
        // 1. Fetch exam metadata
        LocalDate examDate = null;
        LocalTime baseTime = LocalTime.of(9, 0);
        int durationMin = 120;
        try (PreparedStatement ps = conn
                .prepareStatement("SELECT exam_date, exam_time, duration FROM exams WHERE id=?")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date d = rs.getDate("exam_date");
                    if (d != null)
                        examDate = d.toLocalDate();
                    Time t = rs.getTime("exam_time");
                    if (t != null)
                        baseTime = t.toLocalTime();
                    String dur = rs.getString("duration");
                    if (dur != null)
                        durationMin = parseDurMinutes(dur);
                }
            }
        }
        if (examDate == null)
            examDate = LocalDate.now().plusDays(1);
        if (baseTime.isBefore(LocalTime.of(8, 0)) || baseTime.isAfter(LocalTime.of(17, 0)))
            baseTime = LocalTime.of(9, 0);

        // 2. Build occupied intervals for that date
        class Slot {
            LocalTime start;
            LocalTime end;
            String room;

            Slot(LocalTime s, LocalTime e, String r) {
                start = s;
                end = e;
                room = r;
            }
        }
        java.util.List<Slot> busy = new java.util.ArrayList<Slot>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT scheduled_time, room FROM student_exams WHERE scheduled_date=? AND scheduled_time IS NOT NULL AND room IS NOT NULL")) {
            ps.setDate(1, java.sql.Date.valueOf(examDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Time st = rs.getTime("scheduled_time");
                    String room = rs.getString("room");
                    if (st != null && room != null) {
                        LocalTime s = st.toLocalTime();
                        busy.add(new Slot(s, s.plusMinutes(durationMin), room));
                    }
                }
            }
        }

        // 3. Try to find first free slot
        String[] rooms = { "Main Hall", "Room 101", "Room 102", "Room 103", "Computer Lab 1", "Computer Lab 2" };
        LocalTime chosenStart = null;
        String chosenRoom = null;
        outer: for (LocalTime cur = baseTime; !cur.plusMinutes(durationMin).isAfter(LocalTime.of(17, 0)); cur = cur
                .plusMinutes(30)) {
            LocalTime end = cur.plusMinutes(durationMin);
            for (String room : rooms) {
                boolean clash = false;
                for (Slot sl : busy) {
                    if (!sl.room.equals(room))
                        continue;
                    if (cur.isBefore(sl.end) && sl.start.isBefore(end)) {
                        clash = true;
                        break;
                    }
                }
                if (!clash) {
                    chosenStart = cur;
                    chosenRoom = room;
                    break outer;
                }
            }
        }
        if (chosenStart == null) {
            chosenStart = baseTime;
            chosenRoom = rooms[0];
        }

        // 4. Persist schedule (still inside caller's transaction)
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE student_exams SET scheduled_date=?, scheduled_time=?, room=?, status=CASE WHEN status='Pending' THEN 'Enrolled' ELSE status END WHERE id=?")) {
            upd.setDate(1, java.sql.Date.valueOf(examDate));
            upd.setTime(2, java.sql.Time.valueOf(chosenStart));
            upd.setString(3, chosenRoom);
            upd.setInt(4, registrationId);
            upd.executeUpdate();
        }

        // 5. Confirm to user
        JOptionPane.showMessageDialog(this,
                "✅ Enrollment successful!\n\nExam ID: " + examId +
                        "\nDate: " + examDate +
                        "\nTime: " + chosenStart +
                        "\nRoom: " + chosenRoom,
                "Exam Scheduled", JOptionPane.INFORMATION_MESSAGE);
    }

    private int parseDurMinutes(String d) {
        if (d == null)
            return 120;
        d = d.toLowerCase();
        if (d.contains("1.5"))
            return 90;
        if (d.contains("2.5"))
            return 150;
        if (d.contains("3"))
            return 180;
        if (d.contains("2"))
            return 120;
        if (d.contains("1"))
            return 60;
        try {
            return Integer.parseInt(d.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 120;
        }
    }

    private int getSelectedExamId(int visibleRowIndex) {
        int modelIndex = examTable.convertRowIndexToModel(visibleRowIndex);
        Object idValue = ((DefaultTableModel) examTable.getModel()).getValueAt(modelIndex, 0);
        return Integer.parseInt(idValue.toString());
    }
}
