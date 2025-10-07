package views;

import dao.DatabaseConnection;
import java.awt.*;
import java.sql.*;
import dao.SchedulingService;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class ExamEnrollmentSystem extends JFrame {

    private final int studentId;
    private Connection conn;

    private JPanel mainContent;
    private JLabel lblName;
    private JLabel lblCourse;
    private JLabel lblBalance;
    private JTable tblUpcoming;
    private JTable tblHistory;
    private JTable tblManageExams;

    private static final int EXAM_FEE = 300; // Default exam fee

    public ExamEnrollmentSystem(int studentId) {
        this.studentId = studentId;
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

        // Main content
        mainContent = new JPanel(new CardLayout());
        add(mainContent, BorderLayout.CENTER);

        mainContent.add(createDashboardPanel(), "DASHBOARD");
        mainContent.add(createMyMarksPanel(), "MYMARKS");
        mainContent.add(new ManageExamsPanel(studentId), "MANAGE");

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

    // =================== DASHBOARD ===================

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(null);
        panel.setBackground(new Color(245, 248, 255));

        // Balance card
        JPanel cardBalance = createInfoCard("Wallet Balance", "₱0.00", 30, 20);
        lblBalance = new JLabel("₱0.00");
        lblBalance.setBounds(50, 60, 200, 30);
        lblBalance.setForeground(Color.WHITE);
        lblBalance.setFont(new Font("Arial", Font.BOLD, 18));
        cardBalance.add(lblBalance);
        panel.add(cardBalance);

        // Cash-In button
        JButton btnCashIn = new JButton("💰 Cash In");
        btnCashIn.setBounds(340, 50, 120, 36);
        btnCashIn.setBackground(new Color(34, 139, 34));
        btnCashIn.setForeground(Color.WHITE);
        btnCashIn.setFocusPainted(false);
        btnCashIn.addActionListener(e -> openCashIn());
        panel.add(btnCashIn);

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
            loadBalance();
        });
        panel.add(btnRefreshUp);

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

    // =================== LOADERS ===================

    private void loadStudentInfo() {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT s.name, c.name AS course_name FROM students s LEFT JOIN courses c ON s.course_id=c.id WHERE s.id=?")) {
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                lblName.setText(rs.getString("name"));
                lblCourse.setText(rs.getString("course_name"));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadBalance() {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM students WHERE id=?")) {
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                lblBalance.setText(String.format("₱%.2f", rs.getDouble("balance")));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            lblBalance.setText("₱0.00");
        }
    }

    private void loadUpcomingExams() {
        try {
            // First auto-schedule any enrolled exams missing schedule info
            try (PreparedStatement uns = conn.prepareStatement(
                    "SELECT exam_id FROM student_exams WHERE student_id=? AND status IN ('Pending','Enrolled') AND scheduled_date IS NULL")) {
                uns.setInt(1, studentId);
                try (ResultSet ur = uns.executeQuery()) {
                    while (ur.next()) {
                        SchedulingService.autoScheduleExam(studentId, ur.getInt(1));
                    }
                }
            }

            String sql = "SELECT se.id AS reg_id, e.id AS exam_id, e.exam_name, "
                    + "COALESCE(se.scheduled_date, e.exam_date) AS display_date, "
                    + "COALESCE(se.scheduled_time, e.exam_time) AS display_time, "
                    + "COALESCE(se.room, e.room) AS room, e.duration, se.status, se.is_paid "
                    + "FROM student_exams se JOIN exams e ON se.exam_id = e.id "
                    + "WHERE se.student_id=? AND se.status <> 'Cancelled' "
                    + "ORDER BY display_date, display_time";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            DefaultTableModel model = new DefaultTableModel(
                    new Object[] { "RegID", "ExamID", "Exam", "Date", "Time", "Room", "Duration", "Status", "Paid" },
                    0);
            int rows = 0;
            while (rs.next()) {
                int regId = rs.getInt("reg_id");
                int examId = rs.getInt("exam_id");
                String examName = rs.getString("exam_name");
                Date displayDate = rs.getDate("display_date");
                Time displayTime = rs.getTime("display_time");
                String room = rs.getString("room");
                String duration = rs.getString("duration");
                String status = rs.getString("status");
                boolean paid = rs.getInt("is_paid") == 1;

                model.addRow(new Object[] {
                        regId,
                        examId,
                        examName,
                        displayDate,
                        displayTime != null ? displayTime.toString() : "TBA",
                        room != null ? room : "TBA",
                        duration != null ? duration : "TBA",
                        status != null ? status : "Unknown",
                        paid ? "Paid" : "Unpaid"
                });
                rows++;
            }

            tblUpcoming.setModel(model);
            // hide ID columns but keep them in model for actions
            if (tblUpcoming.getColumnModel().getColumnCount() > 0) {
                tblUpcoming.removeColumn(tblUpcoming.getColumnModel().getColumn(0));
                if (tblUpcoming.getColumnModel().getColumnCount() > 0) {
                    tblUpcoming.removeColumn(tblUpcoming.getColumnModel().getColumn(0));
                }
            }
            System.out.println("[DEBUG] loadUpcomingExams: rows returned=" + rows + " for studentId=" + studentId);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private JPanel createMyMarksPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        JLabel label = new JLabel("Completed Exams / My Marks");
        label.setFont(new Font("Arial", Font.BOLD, 16));
        panel.add(label, BorderLayout.NORTH);
        tblHistory = new JTable();
        panel.add(new JScrollPane(tblHistory), BorderLayout.CENTER);
        return panel;
    }

    // =================== BUTTON LOGIC ===================

    private void openCashIn() {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM students WHERE id=?")) {
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String name = rs.getString("name");
                PaymentForm pf = new PaymentForm(this, studentId, name, 0);
                pf.setVisible(true);
                loadBalance();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void showDashboardView() {
        ((CardLayout) mainContent.getLayout()).show(mainContent, "DASHBOARD");
        loadBalance();
        loadUpcomingExams();
    }

    private void showMyMarksView() {
        ((CardLayout) mainContent.getLayout()).show(mainContent, "MYMARKS");
    }

    private void showManageView() {
        ((CardLayout) mainContent.getLayout()).show(mainContent, "MANAGE");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ExamEnrollmentSystem frame = new ExamEnrollmentSystem(1);
            frame.setVisible(true);
        });
    }
}
