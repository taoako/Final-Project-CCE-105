package views;

import dao.DatabaseConnection;
import java.awt.*;
import java.sql.*;
import dao.SchedulingService;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExamEnrollmentSystem extends JFrame {

    private final int studentId;
    private Connection conn;

    // Color scheme for modern UI
    private static final Color PRIMARY_COLOR = new Color(45, 52, 68);
    private static final Color SECONDARY_COLOR = new Color(66, 73, 91);
    private static final Color ACCENT_COLOR = new Color(0, 123, 255);
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69);
    private static final Color WARNING_COLOR = new Color(255, 193, 7);
    private static final Color DANGER_COLOR = new Color(220, 53, 69);
    private static final Color LIGHT_COLOR = new Color(248, 249, 250);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_COLOR = new Color(33, 37, 41);
    private static final Color MUTED_COLOR = new Color(108, 117, 125);

    private JPanel mainContent;
    private JLabel lblName;
    private JLabel lblCourse;
    private JLabel lblBalance;
    private JTable tblUpcoming;
    private JTable tblManageExams;
    private CardLayout cardLayout;

    private static final int EXAM_FEE = 300; // Default exam fee

    public ExamEnrollmentSystem(int studentId) {
        this.studentId = studentId;
        initializeDb();
        initUI();
        loadStudentInfo();
        loadBalance();
        loadUpcomingExams(); // Load exam data immediately
        updateStatsCards(); // Update statistics
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
        setTitle("🎓 Student Dashboard - Exam Enrollment System");
        setSize(1400, 900);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLayout(new BorderLayout());

        // Set modern look and feel
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // Use default look and feel
        }

        createSidebar();
        createTopBar();
        createMainContent();
    }

    private void createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(PRIMARY_COLOR);
        sidebar.setPreferredSize(new Dimension(280, getHeight()));
        sidebar.setBorder(new EmptyBorder(0, 0, 0, 1));

        // Header
        JPanel headerPanel = createSidebarHeader();
        sidebar.add(headerPanel);
        sidebar.add(Box.createVerticalStrut(30));

        // Student info
        JPanel studentInfo = createStudentInfoPanel();
        sidebar.add(studentInfo);
        sidebar.add(Box.createVerticalStrut(30));

        // Navigation buttons
        addNavigationButton(sidebar, "📊 Dashboard", "dashboard", true);
        addNavigationButton(sidebar, "📝 My Exams", "myexams", false);
        addNavigationButton(sidebar, "📋 Manage Exams", "manage", false);
        addNavigationButton(sidebar, "📈 My Marks", "marks", false);

        sidebar.add(Box.createVerticalGlue());

        // Logout button at bottom
        JButton logoutBtn = createStyledButton("🚪 Logout", DANGER_COLOR);
        logoutBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoutBtn.setMaximumSize(new Dimension(220, 45));
        logoutBtn.addActionListener(e -> {
            int option = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to logout?",
                    "Confirm Logout",
                    JOptionPane.YES_NO_OPTION);

            if (option == JOptionPane.YES_OPTION) {
                dispose();
                new LoginFormGUI().setVisible(true);
            }
        });

        sidebar.add(logoutBtn);
        sidebar.add(Box.createVerticalStrut(20));

        add(sidebar, BorderLayout.WEST);
    }

    private JPanel createSidebarHeader() {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Logo/Icon
        JLabel logoLabel = new JLabel("🎓", SwingConstants.CENTER);
        logoLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        logoLabel.setForeground(Color.WHITE);
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Title
        JLabel titleLabel = new JLabel("STUDENT PORTAL", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Subtitle
        JLabel subtitleLabel = new JLabel("Exam Enrollment", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(200, 200, 200));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(logoLabel);
        headerPanel.add(Box.createVerticalStrut(10));
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(5));
        headerPanel.add(subtitleLabel);

        return headerPanel;
    }

    private JPanel createStudentInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SECONDARY_COLOR);
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));
        panel.setMaximumSize(new Dimension(260, 120));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Student name
        lblName = new JLabel("Loading...");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblName.setForeground(Color.WHITE);
        lblName.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Course
        lblCourse = new JLabel("Course");
        lblCourse.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblCourse.setForeground(new Color(200, 200, 200));
        lblCourse.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Balance
        lblBalance = new JLabel("₱0.00");
        lblBalance.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblBalance.setForeground(SUCCESS_COLOR);
        lblBalance.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(lblName);
        panel.add(Box.createVerticalStrut(5));
        panel.add(lblCourse);
        panel.add(Box.createVerticalStrut(8));
        panel.add(new JLabel("💰 Balance:", SwingConstants.CENTER) {
            {
                setFont(new Font("Segoe UI", Font.PLAIN, 11));
                setForeground(new Color(200, 200, 200));
                setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        });
        panel.add(lblBalance);

        return panel;
    }

    private void addNavigationButton(JPanel sidebar, String text, String cardName, boolean selected) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(selected ? SECONDARY_COLOR : PRIMARY_COLOR);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(new EmptyBorder(15, 25, 15, 25));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                button.setBackground(SECONDARY_COLOR);
            }

            public void mouseExited(MouseEvent evt) {
                if (!cardName.equals(getCurrentCard())) {
                    button.setBackground(PRIMARY_COLOR);
                }
            }
        });

        button.addActionListener(e -> {
            resetNavigationButtons(sidebar);
            button.setBackground(SECONDARY_COLOR);

            switch (cardName) {
                case "dashboard" -> showDashboardView();
                case "myexams" -> showMyExamsView();
                case "manage" -> showManageView();
                case "marks" -> showMyMarksView();
            }
        });

        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(5));
    }

    private String getCurrentCard() {
        return "dashboard"; // This would need to be tracked properly
    }

    private void resetNavigationButtons(JPanel sidebar) {
        for (Component comp : sidebar.getComponents()) {
            if (comp instanceof JButton && !((JButton) comp).getText().contains("Logout")) {
                comp.setBackground(PRIMARY_COLOR);
            }
        }
    }

    private void createTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(CARD_COLOR);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(222, 226, 230)),
                new EmptyBorder(15, 30, 15, 30)));

        // Welcome message and date
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(CARD_COLOR);

        JLabel welcomeLabel = new JLabel("Welcome to Your Dashboard");
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        welcomeLabel.setForeground(TEXT_COLOR);

        JLabel dateLabel = new JLabel(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy - HH:mm")));
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        dateLabel.setForeground(MUTED_COLOR);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(CARD_COLOR);
        textPanel.add(welcomeLabel);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(dateLabel);

        leftPanel.add(textPanel);

        // Quick actions
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.setBackground(CARD_COLOR);

        JButton cashInBtn = createStyledButton("💰 Cash In", SUCCESS_COLOR);
        cashInBtn.addActionListener(e -> openCashIn());

        JButton refreshBtn = createStyledButton("🔄 Refresh", ACCENT_COLOR);
        refreshBtn.addActionListener(e -> refreshData());

        rightPanel.add(cashInBtn);
        rightPanel.add(Box.createHorizontalStrut(10));
        rightPanel.add(refreshBtn);

        topBar.add(leftPanel, BorderLayout.WEST);
        topBar.add(rightPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);
    }

    private void createMainContent() {
        cardLayout = new CardLayout();
        mainContent = new JPanel(cardLayout);
        mainContent.setBackground(LIGHT_COLOR);

        // Create panels
        mainContent.add(createDashboardPanel(), "dashboard");
        mainContent.add(createMyExamsPanel(), "myexams");
        mainContent.add(new ManageExamsPanel(studentId), "manage");
        mainContent.add(createMyMarksPanel(), "marks");

        add(mainContent, BorderLayout.CENTER);
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, 20, 10, 20));

        // Hover effect
        Color originalColor = bgColor;
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                button.setBackground(bgColor.darker());
            }

            public void mouseExited(MouseEvent evt) {
                button.setBackground(originalColor);
            }
        });

        return button;
    }

    // =================== DASHBOARD ===================

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(LIGHT_COLOR);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Stats cards at top
        JPanel statsPanel = createStatsPanel();
        panel.add(statsPanel, BorderLayout.NORTH);

        // Main content: make upcoming exams take the majority of the space
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(LIGHT_COLOR);
        contentPanel.setBorder(new EmptyBorder(20, 0, 0, 0));

        // Upcoming exams section (expanded)
        JPanel upcomingSection = createUpcomingExamsSection();
        upcomingSection.setPreferredSize(new Dimension(800, 520)); // make it taller
        contentPanel.add(upcomingSection, BorderLayout.CENTER);

        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 20, 0));
        panel.setBackground(LIGHT_COLOR);

        // These will be populated with actual data
        panel.add(createStatsCard("💰 Balance", "₱0.00", "Current wallet balance", SUCCESS_COLOR));
        panel.add(createStatsCard("📝 Enrolled", "0", "Active exam enrollments", ACCENT_COLOR));
        panel.add(createStatsCard("✅ Completed", "0", "Exams completed", SUCCESS_COLOR));
        panel.add(createStatsCard("⏳ Pending", "0", "Awaiting payment", WARNING_COLOR));

        return panel;
    }

    private JPanel createStatsCard(String title, String value, String subtitle, Color accentColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1),
                new EmptyBorder(20, 20, 20, 20)));

        // Icon and value row
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(CARD_COLOR);

        String[] parts = title.split(" ", 2);
        JLabel iconLabel = new JLabel(parts[0]);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        iconLabel.setForeground(accentColor);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        valueLabel.setForeground(TEXT_COLOR);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        topRow.add(iconLabel, BorderLayout.WEST);
        topRow.add(valueLabel, BorderLayout.EAST);

        // Title
        JLabel titleLabel = new JLabel(parts.length > 1 ? parts[1] : title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Subtitle
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(MUTED_COLOR);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(topRow);
        card.add(Box.createVerticalStrut(15));
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(subtitleLabel);

        return card;
    }

    private JPanel createUpcomingExamsSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(CARD_COLOR);
        section.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1),
                new EmptyBorder(25, 25, 25, 25)));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(CARD_COLOR);

        JLabel titleLabel = new JLabel("📅 Upcoming Exams");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(TEXT_COLOR);

        JButton refreshBtn = createStyledButton("🔄 Refresh", ACCENT_COLOR);
        refreshBtn.addActionListener(e -> {
            loadUpcomingExams();
            updateStatsCards();
        });

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(refreshBtn, BorderLayout.EAST);
        headerPanel.setBorder(new EmptyBorder(0, 0, 15, 0));

        // Table
        tblUpcoming = createStyledTable();
        JScrollPane scrollPane = new JScrollPane(tblUpcoming);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Color.WHITE);

        section.add(headerPanel, BorderLayout.NORTH);
        section.add(scrollPane, BorderLayout.CENTER);

        return section;
    }

    private JPanel createHistorySection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(CARD_COLOR);
        section.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1),
                new EmptyBorder(25, 25, 25, 25)));

        // Header
        JLabel titleLabel = new JLabel("📋 Recent History");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));

        // Table removed - recent history section has been removed per UI update
        section.add(titleLabel, BorderLayout.NORTH);
        // No table added here

        return section;
    }

    private JTable createStyledTable() {
        JTable table = new JTable();
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setRowHeight(35);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setGridColor(new Color(222, 226, 230));
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(true);
        table.setSelectionBackground(ACCENT_COLOR.brighter());
        table.setSelectionForeground(Color.WHITE);

        // Custom header renderer
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setBackground(LIGHT_COLOR);
                c.setForeground(TEXT_COLOR);
                setFont(new Font("Segoe UI", Font.BOLD, 12));
                setBorder(new EmptyBorder(8, 12, 8, 12));
                return c;
            }
        });

        return table;
    }

    private JPanel createMyExamsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(LIGHT_COLOR);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(LIGHT_COLOR);

        JLabel titleLabel = new JLabel("📝 My Exam Enrollments");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_COLOR);

        JLabel subtitleLabel = new JLabel("View and manage your exam enrollments");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        subtitleLabel.setForeground(MUTED_COLOR);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(LIGHT_COLOR);
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.setBorder(new EmptyBorder(0, 0, 30, 0));

        // Content
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(CARD_COLOR);
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1),
                new EmptyBorder(25, 25, 25, 25)));

        // This will show the same data as upcoming exams but with different layout
        JTable myExamsTable = createStyledTable();
        JScrollPane scrollPane = new JScrollPane(myExamsTable);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Color.WHITE);

        contentPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
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

    private void updateStatsCards() {
        // Update various statistics on the dashboard
        loadBalance();
        updateExamStatistics();
    }

    private void updateExamStatistics() {
        try {
            // Count enrolled exams
            int enrolledCount = 0;
            int completedCount = 0;
            int pendingCount = 0;

            String sql = "SELECT status, COUNT(*) as count FROM student_exams WHERE student_id=? GROUP BY status";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String status = rs.getString("status");
                int count = rs.getInt("count");

                switch (status.toLowerCase()) {
                    case "enrolled":
                        enrolledCount = count;
                        break;
                    case "completed":
                        completedCount = count;
                        break;
                    case "pending":
                        pendingCount = count;
                        break;
                }
            }

            // Update stats cards (this would need references to the actual cards)
            // For now, we'll print the statistics
            System.out.println("[STATS] Enrolled: " + enrolledCount + ", Completed: " + completedCount + ", Pending: "
                    + pendingCount);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadUpcomingExams() {
        try {
            // Updated query to match the new database schema
            String sql = "SELECT se.id AS reg_id, e.id AS exam_id, e.exam_name, "
                    + "es.scheduled_date, es.scheduled_time, es.room_number, e.duration, "
                    + "se.status, se.is_paid "
                    + "FROM student_exams se "
                    + "JOIN exam_schedules es ON se.exam_schedule_id = es.id "
                    + "JOIN exams e ON es.exam_id = e.id "
                    + "WHERE se.student_id=? AND se.status <> 'Cancelled' "
                    + "ORDER BY es.scheduled_date, es.scheduled_time";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            DefaultTableModel model = new DefaultTableModel(
                    new Object[] { "Exam", "Date", "Time", "Room", "Duration", "Status", "Payment" },
                    0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            int rows = 0;
            while (rs.next()) {
                String examName = rs.getString("exam_name");
                Date scheduledDate = rs.getDate("scheduled_date");
                Time scheduledTime = rs.getTime("scheduled_time");
                String roomNumber = rs.getString("room_number");
                String duration = rs.getString("duration");
                String status = rs.getString("status");
                boolean paid = rs.getInt("is_paid") == 1;

                model.addRow(new Object[] {
                        examName,
                        scheduledDate != null ? scheduledDate.toString() : "TBA",
                        scheduledTime != null ? scheduledTime.toString() : "TBA",
                        roomNumber != null ? roomNumber : "TBA",
                        duration != null ? duration : "TBA",
                        status != null ? status : "Unknown",
                        paid ? "✅ Paid" : "❌ Unpaid"
                });
                rows++;
            }

            tblUpcoming.setModel(model);

            // Apply custom cell renderer for status column
            if (tblUpcoming.getColumnCount() > 5) {
                tblUpcoming.getColumnModel().getColumn(5).setCellRenderer(new StatusCellRenderer());
            }
            if (tblUpcoming.getColumnCount() > 6) {
                tblUpcoming.getColumnModel().getColumn(6).setCellRenderer(new PaymentCellRenderer());
            }
            System.out.println("[DEBUG] loadUpcomingExams: rows returned=" + rows + " for studentId=" + studentId);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    // --- recent history removed (UI simplified) ---

    private class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                String status = value.toString();
                switch (status.toLowerCase()) {
                    case "enrolled" -> c.setBackground(SUCCESS_COLOR.brighter());
                    case "pending" -> c.setBackground(WARNING_COLOR.brighter());
                    case "cancelled" -> c.setBackground(DANGER_COLOR.brighter());
                    default -> c.setBackground(Color.WHITE);
                }
            }

            return c;
        }
    }

    private class PaymentCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                String payment = value.toString();
                if (payment.contains("Paid")) {
                    c.setBackground(SUCCESS_COLOR.brighter());
                } else {
                    c.setBackground(DANGER_COLOR.brighter());
                }
            }

            return c;
        }
    }

    private JPanel createMyMarksPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(LIGHT_COLOR);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(LIGHT_COLOR);

        JLabel titleLabel = new JLabel("📈 My Marks & Results");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_COLOR);

        JLabel subtitleLabel = new JLabel("View your completed exams and scores");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        subtitleLabel.setForeground(MUTED_COLOR);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(LIGHT_COLOR);
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.setBorder(new EmptyBorder(0, 0, 30, 0));

        // Content
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(CARD_COLOR);
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1),
                new EmptyBorder(25, 25, 25, 25)));

        JTable marksTable = createStyledTable();
        JScrollPane scrollPane = new JScrollPane(marksTable);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Color.WHITE);

        contentPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    // =================== BUTTON LOGIC ===================

    private void refreshData() {
        loadBalance();
        loadUpcomingExams();
        updateStatsCards();
        JOptionPane.showMessageDialog(this, "Data refreshed successfully!", "Refresh Complete",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void openCashIn() {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM students WHERE id=?")) {
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String name = rs.getString("name");
                PaymentForm pf = new PaymentForm(this, studentId, name, 0);
                pf.setVisible(true);
                loadBalance();
                updateStatsCards();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void showDashboardView() {
        cardLayout.show(mainContent, "dashboard");
        loadBalance();
        loadUpcomingExams();
        updateStatsCards();
    }

    private void showMyExamsView() {
        cardLayout.show(mainContent, "myexams");
        loadUpcomingExams(); // Same data, different view
    }

    private void showMyMarksView() {
        cardLayout.show(mainContent, "marks");
        // Load marks/results data here
    }

    private void showManageView() {
        cardLayout.show(mainContent, "manage");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ExamEnrollmentSystem frame = new ExamEnrollmentSystem(1);
            frame.setVisible(true);
        });
    }
}
