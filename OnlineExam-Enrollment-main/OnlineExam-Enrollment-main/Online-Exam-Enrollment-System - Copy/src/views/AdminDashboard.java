package views;

import dao.*;
import models.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.util.List;

public class AdminDashboard extends JFrame {

    // Color scheme for modern UI
    private static final Color PRIMARY_COLOR = new Color(45, 52, 68); // Dark blue-gray
    private static final Color SECONDARY_COLOR = new Color(66, 73, 91); // Lighter blue-gray
    private static final Color ACCENT_COLOR = new Color(0, 123, 255); // Bootstrap blue
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69); // Bootstrap green
    private static final Color WARNING_COLOR = new Color(255, 193, 7); // Bootstrap warning
    private static final Color DANGER_COLOR = new Color(220, 53, 69); // Bootstrap red
    private static final Color LIGHT_COLOR = new Color(248, 249, 250); // Light gray
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_COLOR = new Color(33, 37, 41);
    private static final Color MUTED_COLOR = new Color(108, 117, 125);

    // DAOs
    private ExamDAO examDAO;
    private RoomDAO roomDAO;
    private StudentDAO studentDAO;
    private CourseDAO courseDAO;

    // Main components
    private JPanel mainPanel;
    private JPanel sidebarPanel;
    private JPanel contentPanel;
    private CardLayout cardLayout;

    // Current admin
    private Admin currentAdmin;

    public AdminDashboard(Admin admin) {
        this.currentAdmin = admin;
        this.examDAO = new ExamDAO();
        this.roomDAO = new RoomDAO();
        this.studentDAO = new StudentDAO();
        this.courseDAO = new CourseDAO();

        initializeUI();
        loadDashboard();
    }

    private void initializeUI() {
        setTitle("🚀 Admin Control Center - " + currentAdmin.getUsername());
        setSize(1400, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        // Set look and feel
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

        setupMainLayout();
        createSidebar();
        createContentArea();

        setVisible(true);
    }

    private void setupMainLayout() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(LIGHT_COLOR);

        add(mainPanel);
    }

    private void createSidebar() {
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(PRIMARY_COLOR);
        sidebarPanel.setPreferredSize(new Dimension(280, getHeight()));
        sidebarPanel.setBorder(new EmptyBorder(0, 0, 0, 1));

        // Header
        JPanel headerPanel = createSidebarHeader();
        sidebarPanel.add(headerPanel);
        sidebarPanel.add(Box.createVerticalStrut(30));

        // Navigation buttons
        addNavigationButton("📊 Dashboard", "dashboard", true);
        addNavigationButton("📝 Manage Exams", "exams", false);
        addNavigationButton("🏢 Manage Rooms", "rooms", false);
        addNavigationButton("👥 Manage Students", "students", false);
        addNavigationButton("📅 View Schedules", "schedules", false);
        addNavigationButton("💰 Financial Reports", "finance", false);
        addNavigationButton("⚙️ System Settings", "settings", false);

        sidebarPanel.add(Box.createVerticalGlue());

        // Logout button at bottom
        JButton logoutBtn = createStyledButton("🚪 Logout", DANGER_COLOR);
        logoutBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoutBtn.setMaximumSize(new Dimension(220, 45));
        logoutBtn.addActionListener(event -> {
            int option = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to logout?",
                    "Confirm Logout",
                    JOptionPane.YES_NO_OPTION);

            if (option == JOptionPane.YES_OPTION) {
                dispose();
                new LoginFormGUI().setVisible(true);
            }
        });

        sidebarPanel.add(logoutBtn);
        sidebarPanel.add(Box.createVerticalStrut(20));

        mainPanel.add(sidebarPanel, BorderLayout.WEST);
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
        JLabel titleLabel = new JLabel("ADMIN PANEL", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Subtitle
        JLabel subtitleLabel = new JLabel("Exam Management System", SwingConstants.CENTER);
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

    private void addNavigationButton(String text, String cardName, boolean selected) {
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
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (!button.getBackground().equals(SECONDARY_COLOR)) {
                    button.setBackground(SECONDARY_COLOR);
                }
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (!cardName.equals(getCurrentCard())) {
                    button.setBackground(PRIMARY_COLOR);
                }
            }
        });

        button.addActionListener(event -> {
            // Reset all buttons
            resetNavigationButtons();
            // Set this button as selected
            button.setBackground(SECONDARY_COLOR);
            // Show the corresponding panel
            cardLayout.show(contentPanel, cardName);

            // Load specific data for each panel
            switch (cardName) {
                case "dashboard" -> loadDashboard();
                case "exams" -> loadExamsPanel();
                case "rooms" -> loadRoomsPanel();
                case "students" -> loadStudentsPanel();
                case "schedules" -> loadSchedulesPanel();
                case "finance" -> loadFinancePanel();
                case "settings" -> loadSettingsPanel();
            }
        });

        sidebarPanel.add(button);
        sidebarPanel.add(Box.createVerticalStrut(5));
    }

    private void resetNavigationButtons() {
        for (Component comp : sidebarPanel.getComponents()) {
            if (comp instanceof JButton && !((JButton) comp).getText().contains("Logout")) {
                comp.setBackground(PRIMARY_COLOR);
            }
        }
    }

    private String getCurrentCard() {
        // This would need to be tracked, for now return dashboard
        return "dashboard";
    }

    private void createContentArea() {
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(LIGHT_COLOR);

        // Create all panels
        contentPanel.add(createDashboardPanel(), "dashboard");
        contentPanel.add(createExamsPanel(), "exams");
        contentPanel.add(createRoomsPanel(), "rooms");
        contentPanel.add(createStudentsPanel(), "students");
        contentPanel.add(createSchedulesPanel(), "schedules");
        contentPanel.add(createFinancePanel(), "finance");
        contentPanel.add(createSettingsPanel(), "settings");

        mainPanel.add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(LIGHT_COLOR);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(LIGHT_COLOR);

        JLabel titleLabel = new JLabel("📊 Dashboard Overview");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_COLOR);

        JLabel dateLabel = new JLabel(java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy - HH:mm")));
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        dateLabel.setForeground(MUTED_COLOR);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(dateLabel, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        // Stats cards
        JPanel statsPanel = createStatsPanel();
        panel.add(statsPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 4, 20, 20));
        panel.setBackground(LIGHT_COLOR);
        panel.setBorder(new EmptyBorder(30, 0, 0, 0));

        // Stats cards will be populated in loadDashboard()
        return panel;
    }

    private JPanel createStatsCard(String title, String value, String subtitle, Color accentColor, String icon) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1),
                new EmptyBorder(25, 25, 25, 25)));

        // Icon and value row
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(CARD_COLOR);

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        iconLabel.setForeground(accentColor);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        valueLabel.setForeground(TEXT_COLOR);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        topRow.add(iconLabel, BorderLayout.WEST);
        topRow.add(valueLabel, BorderLayout.EAST);

        // Title
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
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

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(12, 24, 12, 24));

        // Hover effect
        Color originalColor = bgColor;
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor.darker());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(originalColor);
            }
        });

        return button;
    }

    // Panel creation methods for other sections
    private JPanel createExamsPanel() {
        ExamManagementPanel examPanel = new ExamManagementPanel();
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBackground(LIGHT_COLOR);
        wrapperPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(LIGHT_COLOR);

        JLabel titleLabel = new JLabel("📝 Exam Management");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_COLOR);

        JLabel subtitleLabel = new JLabel("Add, edit, and manage examination schedules");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        subtitleLabel.setForeground(MUTED_COLOR);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(LIGHT_COLOR);
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.setBorder(new EmptyBorder(0, 0, 20, 0));

        wrapperPanel.add(headerPanel, BorderLayout.NORTH);
        wrapperPanel.add(examPanel, BorderLayout.CENTER);

        return wrapperPanel;
    }

    private JPanel createRoomsPanel() {
        RoomManagementPanel roomPanel = new RoomManagementPanel();
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBackground(LIGHT_COLOR);
        wrapperPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(LIGHT_COLOR);

        JLabel titleLabel = new JLabel("🏢 Room Management");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_COLOR);

        JLabel subtitleLabel = new JLabel("Configure examination rooms and their capacity");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        subtitleLabel.setForeground(MUTED_COLOR);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(LIGHT_COLOR);
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.setBorder(new EmptyBorder(0, 0, 20, 0));

        wrapperPanel.add(headerPanel, BorderLayout.NORTH);
        wrapperPanel.add(roomPanel, BorderLayout.CENTER);

        return wrapperPanel;
    }

    private JPanel createStudentsPanel() {
        return createGenericPanel("👥 Student Management", "View and manage student accounts and enrollments");
    }

    private JPanel createSchedulesPanel() {
        return createGenericPanel("📅 Schedule Overview", "Monitor and adjust examination schedules");
    }

    private JPanel createFinancePanel() {
        return createGenericPanel("💰 Financial Reports", "Track payments and generate financial reports");
    }

    private JPanel createSettingsPanel() {
        return createGenericPanel("⚙️ System Settings", "Configure system preferences and admin accounts");
    }

    private JPanel createGenericPanel(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(LIGHT_COLOR);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(LIGHT_COLOR);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_COLOR);

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        subtitleLabel.setForeground(MUTED_COLOR);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(LIGHT_COLOR);
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);

        headerPanel.add(titlePanel, BorderLayout.WEST);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Content area - will be populated by specific load methods
        JPanel contentArea = new JPanel(new BorderLayout());
        contentArea.setBackground(CARD_COLOR);
        contentArea.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1),
                new EmptyBorder(30, 30, 30, 30)));

        JLabel placeholderLabel = new JLabel("Content will be loaded here...", SwingConstants.CENTER);
        placeholderLabel.setFont(new Font("Segoe UI", Font.ITALIC, 16));
        placeholderLabel.setForeground(MUTED_COLOR);
        contentArea.add(placeholderLabel);

        panel.add(contentArea, BorderLayout.CENTER);

        return panel;
    }

    // Data loading methods
    private void loadDashboard() {
        // This will be implemented to load actual statistics
        SwingUtilities.invokeLater(() -> {
            JPanel statsPanel = (JPanel) ((JPanel) contentPanel.getComponent(0)).getComponent(1);
            statsPanel.removeAll();

            // Add actual stats cards
            statsPanel.add(createStatsCard("Total Students", "156", "Active enrollments", ACCENT_COLOR, "👥"));
            statsPanel.add(createStatsCard("Active Exams", "24", "Scheduled this month", SUCCESS_COLOR, "📝"));
            statsPanel.add(createStatsCard("Available Rooms", "12", "Ready for scheduling", WARNING_COLOR, "🏢"));
            statsPanel.add(createStatsCard("Total Revenue", "$12,340", "From exam fees", SUCCESS_COLOR, "💰"));

            statsPanel.add(createStatsCard("Pending Schedules", "8", "Awaiting assignment", DANGER_COLOR, "⏳"));
            statsPanel.add(createStatsCard("Completed Exams", "45", "This semester", SUCCESS_COLOR, "✅"));
            statsPanel.add(createStatsCard("System Uptime", "99.9%", "Service availability", SUCCESS_COLOR, "⚡"));
            statsPanel.add(createStatsCard("Admin Users", "3", "Active administrators", ACCENT_COLOR, "👨‍💼"));

            statsPanel.revalidate();
            statsPanel.repaint();
        });
    }

    private void loadExamsPanel() {
        // Implementation for exam management
    }

    private void loadRoomsPanel() {
        // Implementation for room management
    }

    private void loadStudentsPanel() {
        // Implementation for student management
    }

    private void loadSchedulesPanel() {
        // Implementation for schedule management
    }

    private void loadFinancePanel() {
        // Implementation for financial reports
    }

    private void loadSettingsPanel() {
        // Implementation for system settings
    }
}