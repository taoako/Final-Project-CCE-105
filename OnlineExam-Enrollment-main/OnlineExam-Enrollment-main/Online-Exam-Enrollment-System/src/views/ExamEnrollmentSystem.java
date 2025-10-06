package views;

import dao.DatabaseConnection;
import java.awt.*;
import java.sql.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class ExamEnrollmentSystem extends JFrame {

    private final int studentId;
    private Connection conn;

    private JPanel mainContent;
    private JLabel lblName;
    private JLabel lblCourse;
    private JLabel lblTotalPayment;
    private JTable tblUpcoming;
    private JTable tblHistory;
    private JTable tblManageExams;

    private static final int EXAM_FEE = 300; // Updated to match database default

    public ExamEnrollmentSystem(int studentId) {
        this.studentId = studentId;
        System.out.println("[DEBUG] ExamEnrollmentSystem: starting for studentId=" + studentId);
        initializeDb();
        initUI();
        loadStudentInfo();
        showDashboardView();
    }

    private void initializeDb() {
        conn = DatabaseConnection.getConnection();
        if (conn == null) {
            JOptionPane.showMessageDialog(this, "Cannot connect to database. Exiting.", "DB Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void initUI() {
        setTitle("Online Exam Enrollment System");
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Sidebar
        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(30, 144, 255));
        sidebar.setPreferredSize(new Dimension(220, getHeight()));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(20, 10, 20, 10));

        lblName = new JLabel("Name");
        lblName.setForeground(Color.WHITE);
        lblName.setFont(new Font("Arial", Font.BOLD, 16));
        lblName.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblCourse = new JLabel("Course");
        lblCourse.setForeground(Color.WHITE);
        lblCourse.setAlignmentX(Component.CENTER_ALIGNMENT);

        sidebar.add(lblName);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(lblCourse);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        JButton btnDashboard = styledSidebarButton("Dashboard");
        JButton btnMyMarks = styledSidebarButton("My Marks");
        JButton btnManage = styledSidebarButton("Manage Exams");
        JButton btnLogout = styledSidebarButton("Logout");

        sidebar.add(btnDashboard);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(btnMyMarks);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(btnManage);
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(btnLogout);

        add(sidebar, BorderLayout.WEST);

        // Top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Color.WHITE);
        topBar.setBorder(new EmptyBorder(10, 15, 10, 15));
        JLabel title = new JLabel("Online Exam Enrollment System");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        topBar.add(title, BorderLayout.WEST);
        add(topBar, BorderLayout.NORTH);

        // Main content (dynamic)
        mainContent = new JPanel(new CardLayout());
        add(mainContent, BorderLayout.CENTER);

        mainContent.add(createDashboardPanel(), "DASHBOARD");
        mainContent.add(createMyMarksPanel(), "MYMARKS");
        mainContent.add(createManageExamsPanel(), "MANAGE");

        // Button actions
        btnDashboard.addActionListener(e -> showDashboardView());
        btnMyMarks.addActionListener(e -> showMyMarksView());
        btnManage.addActionListener(e -> showManageView());
        btnLogout.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to logout?",
                    "Confirm Logout",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                dispose();
                new LoginFormGUI().setVisible(true);
            }
        });
    }

    private JButton styledSidebarButton(String text) {
        JButton b = new JButton(text);
        b.setMaximumSize(new Dimension(200, 40));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setBackground(new Color(25, 25, 112));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        return b;
    }

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(null);
        panel.setBackground(new Color(245, 248, 255));

        // Info card
        JPanel cardPayment = createInfoCard("Total Payment", "₱0.00", 30, 20);
        lblTotalPayment = new JLabel("₱0.00");
        lblTotalPayment.setBounds(50, 60, 200, 30);
        lblTotalPayment.setForeground(Color.WHITE);
        lblTotalPayment.setFont(new Font("Arial", Font.BOLD, 18));
        cardPayment.add(lblTotalPayment);
        panel.add(cardPayment);

        JButton btnPayNow = new JButton("Pay All");
        btnPayNow.setBounds(340, 50, 100, 36);
        btnPayNow.setBackground(new Color(34, 139, 34));
        btnPayNow.setForeground(Color.WHITE);
        btnPayNow.setFocusPainted(false);
        btnPayNow.addActionListener(e -> openGenericPayment());
        panel.add(btnPayNow);

        JLabel upLabel = new JLabel("Upcoming Exams");
        upLabel.setBounds(30, 150, 300, 25);
        upLabel.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(upLabel);

        tblUpcoming = new JTable();
        JScrollPane spUp = new JScrollPane(tblUpcoming);
        spUp.setBounds(30, 180, 700, 200);
        panel.add(spUp);

        JButton btnRefreshUp = new JButton("Refresh");
        btnRefreshUp.setBounds(750, 180, 120, 30);
        btnRefreshUp.addActionListener(e -> {
            loadUpcomingExams();
            calculateTotalPayment();
        });
        panel.add(btnRefreshUp);

        JButton btnAction = new JButton("Action");
        btnAction.setBounds(750, 220, 120, 30);
        btnAction.addActionListener(e -> handleUpcomingAction());
        panel.add(btnAction);

        JLabel histLabel = new JLabel("Recent History");
        histLabel.setBounds(30, 400, 300, 25);
        histLabel.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(histLabel);

        tblHistory = new JTable();
        JScrollPane spHist = new JScrollPane(tblHistory);
        spHist.setBounds(30, 430, 700, 120);
        panel.add(spHist);

        return panel;
    }

    private JPanel createInfoCard(String title, String value, int x, int y) {
        JPanel card = new JPanel(null);
        card.setBackground(new Color(70, 130, 180));
        card.setBounds(x, y, 300, 100);
        JLabel t = new JLabel(title);
        t.setForeground(Color.WHITE);
        t.setFont(new Font("Arial", Font.BOLD, 14));
        t.setBounds(15, 10, 260, 20);
        card.add(t);
        return card;
    }

    private JPanel createMyMarksPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setBackground(Color.WHITE);
        top.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel label = new JLabel("Completed Exams / My Marks");
        label.setFont(new Font("Arial", Font.BOLD, 16));
        top.add(label);
        panel.add(top, BorderLayout.NORTH);

        tblHistory = new JTable();
        panel.add(new JScrollPane(tblHistory), BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createManageExamsPanel() {
        JPanel panel = new JPanel(null);
        panel.setBackground(Color.WHITE);

        JLabel label = new JLabel("Available Exams for Your Course");
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setBounds(20, 10, 400, 25);
        panel.add(label);

        tblManageExams = new JTable();
        JScrollPane sp = new JScrollPane(tblManageExams);
        sp.setBounds(20, 40, 700, 350);
        panel.add(sp);

        JButton btnRegister = new JButton("Register for Selected Exam");
        btnRegister.setBounds(20, 410, 220, 30);
        btnRegister.setBackground(new Color(30, 144, 255));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setFocusPainted(false);
        btnRegister.addActionListener(e -> registerSelectedExam());
        panel.add(btnRegister);

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.setBounds(260, 410, 100, 30);
        btnRefresh.addActionListener(e -> loadManageExams());
        panel.add(btnRefresh);

        return panel;
    }

    private void showDashboardView() {
        ((CardLayout) mainContent.getLayout()).show(mainContent, "DASHBOARD");
        loadUpcomingExams();
        loadExamHistory();
        calculateTotalPayment();
    }

    private void showMyMarksView() {
        ((CardLayout) mainContent.getLayout()).show(mainContent, "MYMARKS");
        loadCompletedExams();
    }

    private void showManageView() {
        ((CardLayout) mainContent.getLayout()).show(mainContent, "MANAGE");
        loadManageExams();
    }

    private void loadStudentInfo() {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT s.name, c.name AS course_name FROM students s LEFT JOIN courses c ON s.course_id=c.id WHERE s.id=?")) {
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                lblName.setText(rs.getString("name"));
                String courseName = rs.getString("course_name");
                lblCourse.setText(courseName != null ? courseName : "No Course");
                System.out.println("[DEBUG] loadStudentInfo: name=" + rs.getString("name") + ", course=" + courseName);
            } else {
                System.out.println("[DEBUG] loadStudentInfo: no student row for id=" + studentId);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadUpcomingExams() {
        try {
            String sql = "SELECT se.id AS reg_id, e.id AS exam_id, e.exam_name, e.exam_date, e.duration, se.is_paid, " +
                    "es.scheduled_time, es.room_number " +
                    "FROM student_exams se " +
                    "JOIN exams e ON se.exam_id = e.id " +
                    "LEFT JOIN exam_schedules es ON se.student_id = es.student_id AND se.exam_id = es.exam_id " +
                    "WHERE se.student_id = ? AND e.exam_date >= CURDATE() ORDER BY e.exam_date";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            DefaultTableModel model = new DefaultTableModel(
                    new Object[] { "RegID", "ExamID", "Exam", "Date", "Time", "Room", "Paid" }, 0);
            int rows = 0;
            while (rs.next()) {
                int regId = rs.getInt("reg_id");
                int examId = rs.getInt("exam_id");
                String name = rs.getString("exam_name");
                Date d = rs.getDate("exam_date");
                Time time = rs.getTime("scheduled_time");
                String room = rs.getString("room_number");
                boolean paid = rs.getInt("is_paid") == 1;

                model.addRow(new Object[] {
                        regId,
                        examId,
                        name,
                        d,
                        time != null ? time.toString() : "TBA",
                        room != null ? room : "TBA",
                        paid ? "Paid" : "Unpaid"
                });
                rows++;
            }
            tblUpcoming.setModel(model);
            System.out.println("[DEBUG] loadUpcomingExams: rows returned=" + rows + " for studentId=" + studentId);
            
            // Hide RegID and ExamID columns
            if (tblUpcoming.getColumnModel().getColumnCount() > 0) {
                tblUpcoming.removeColumn(tblUpcoming.getColumnModel().getColumn(0));
                tblUpcoming.removeColumn(tblUpcoming.getColumnModel().getColumn(0));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadExamHistory() {
        try {
            String sql = "SELECT e.exam_name, e.exam_date, se.status, se.is_paid " +
                    "FROM student_exams se JOIN exams e ON se.exam_id = e.id " +
                    "WHERE se.student_id = ? ORDER BY e.exam_date DESC LIMIT 5";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            DefaultTableModel model = new DefaultTableModel(new Object[] { "Exam", "Date", "Status", "Paid" }, 0);
            while (rs.next()) {
                String name = rs.getString("exam_name");
                Date d = rs.getDate("exam_date");
                String status = rs.getString("status");
                boolean paid = rs.getInt("is_paid") == 1;
                model.addRow(new Object[] { name, d, status, paid ? "Paid" : "Unpaid" });
            }
            tblHistory.setModel(model);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadCompletedExams() {
        try {
            String sql = "SELECT e.exam_name, e.exam_date, se.status, se.is_paid " +
                    "FROM student_exams se JOIN exams e ON se.exam_id = e.id " +
                    "WHERE se.student_id = ? AND se.status = 'Completed' ORDER BY e.exam_date DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            int rows = 0;
            DefaultTableModel model = new DefaultTableModel(new Object[] { "Exam", "Date", "Status", "Paid" }, 0);
            while (rs.next()) {
                String name = rs.getString("exam_name");
                Date d = rs.getDate("exam_date");
                String status = rs.getString("status");
                boolean paid = rs.getInt("is_paid") == 1;
                model.addRow(new Object[] { name, d, status, paid ? "Paid" : "Unpaid" });
                rows++;
            }
            tblHistory.setModel(model);
            System.out.println("[DEBUG] loadCompletedExams: rows returned=" + rows + " for studentId=" + studentId);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadManageExams() {
        try {
            int courseId = 0;
            PreparedStatement p1 = conn.prepareStatement("SELECT course_id FROM students WHERE id = ?");
            p1.setInt(1, studentId);
            ResultSet r1 = p1.executeQuery();
            if (r1.next())
                courseId = r1.getInt("course_id");

            String sql = "SELECT e.id, e.exam_name, e.exam_date, e.duration, " +
                    "IF(EXISTS(SELECT 1 FROM student_exams se WHERE se.student_id=? AND se.exam_id=e.id), 'Registered','Not Registered') AS regstatus "
                    +
                    "FROM exams e WHERE e.course_id = ? ORDER BY e.exam_date";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ps.setInt(2, courseId);
            ResultSet rs = ps.executeQuery();

            DefaultTableModel model = new DefaultTableModel(
                    new Object[] { "ExamID", "Exam", "Date", "Duration", "Status" }, 0);
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("exam_name");
                Date d = rs.getDate("exam_date");
                String dur = rs.getString("duration");
                String st = rs.getString("regstatus");
                model.addRow(new Object[] { id, name, d, dur, st });
            }
            tblManageExams.setModel(model);
            
            // Hide ExamID column
            if (tblManageExams.getColumnModel().getColumnCount() > 0) {
                tblManageExams.removeColumn(tblManageExams.getColumnModel().getColumn(0));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void calculateTotalPayment() {
        try {
            String q = "SELECT COUNT(*) FROM student_exams WHERE student_id = ? AND is_paid = 1";
            PreparedStatement p = conn.prepareStatement(q);
            p.setInt(1, studentId);
            ResultSet r = p.executeQuery();
            int countPaid = 0;
            if (r.next())
                countPaid = r.getInt(1);
            int total = countPaid * EXAM_FEE;
            lblTotalPayment.setText("₱" + total + ".00");
        } catch (SQLException ex) {
            ex.printStackTrace();
            lblTotalPayment.setText("₱0.00");
        }
    }

    private void handleUpcomingAction() {
        int row = tblUpcoming.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select an upcoming exam first.", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        DefaultTableModel m = (DefaultTableModel) tblUpcoming.getModel();
        int modelRow = tblUpcoming.convertRowIndexToModel(row);

        // Get hidden columns (RegID and ExamID are at indices 0 and 1 in model)
        int regId = (int) m.getValueAt(modelRow, 0);
        int examId = (int) m.getValueAt(modelRow, 1);
        String examName = (String) m.getValueAt(modelRow, 2);
        Date examDate = (Date) m.getValueAt(modelRow, 3);
        String paidStatus = (String) m.getValueAt(modelRow, 6);

        if ("Unpaid".equalsIgnoreCase(paidStatus)) {
            // Open payment form - CORRECTED constructor call
            PaymentForm pf = new PaymentForm(this, studentId, regId, examId, examName, EXAM_FEE);
            pf.setVisible(true);
        } else {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Start exam \"" + examName + "\" now?",
                    "Start Exam",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                JOptionPane.showMessageDialog(this,
                        "Good luck! (Exam start simulated)",
                        "Exam Started",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }

        loadUpcomingExams();
        loadExamHistory();
        calculateTotalPayment();
    }

    private void registerSelectedExam() {
        int row = tblManageExams.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select an exam to register.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        DefaultTableModel m = (DefaultTableModel) tblManageExams.getModel();
        int modelRow = tblManageExams.convertRowIndexToModel(row);
        String status = (String) m.getValueAt(modelRow, 4);
        String examName = (String) m.getValueAt(modelRow, 1);
        Date examDate = (Date) m.getValueAt(modelRow, 2);

        if ("Registered".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "You already registered for this exam.", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        try {
            PreparedStatement ps = conn
                    .prepareStatement("SELECT id FROM exams WHERE exam_name=? AND exam_date=? LIMIT 1");
            ps.setString(1, examName);
            ps.setDate(2, examDate);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                int examId = rs.getInt("id");
                
                PreparedStatement chk = conn.prepareStatement(
                        "SELECT COUNT(*) FROM student_exams WHERE student_id=? AND exam_id=?");
                chk.setInt(1, studentId);
                chk.setInt(2, examId);
                ResultSet r2 = chk.executeQuery();
                
                if (r2.next() && r2.getInt(1) > 0) {
                    JOptionPane.showMessageDialog(this, "Already registered.", "Info", JOptionPane.INFORMATION_MESSAGE);
                    loadManageExams();
                    return;
                }
                
                PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO student_exams (student_id, exam_id, status, is_paid) VALUES (?, ?, 'Pending', 0)");
                ins.setInt(1, studentId);
                ins.setInt(2, examId);
                int c = ins.executeUpdate();
                
                if (c > 0) {
                    JOptionPane.showMessageDialog(this, 
                            "Registered successfully!\n\nPlease proceed to payment to confirm your enrollment.",
                            "Success", 
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Registration failed.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Exam not found.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error registering: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
        
        loadManageExams();
        loadUpcomingExams();
    }

    private void openGenericPayment() {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM student_exams WHERE student_id = ? AND is_paid = 0");
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();
            int unpaidCount = 0;
            if (rs.next())
                unpaidCount = rs.getInt(1);

            if (unpaidCount == 0) {
                JOptionPane.showMessageDialog(this, "You have no outstanding payments.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            double total = unpaidCount * EXAM_FEE;

            // Use the PaymentForm for "Pay All" - CORRECTED constructor
            PaymentForm pf = new PaymentForm(this, studentId, unpaidCount, total);
            pf.setVisible(true);

            loadUpcomingExams();
            loadExamHistory();
            calculateTotalPayment();

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ExamEnrollmentSystem frame = new ExamEnrollmentSystem(1);
            frame.setVisible(true);
        });
    }
}