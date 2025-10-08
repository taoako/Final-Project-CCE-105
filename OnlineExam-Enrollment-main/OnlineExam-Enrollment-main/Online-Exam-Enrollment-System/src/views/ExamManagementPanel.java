package views;

import dao.*;
import models.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class ExamManagementPanel extends JPanel {

    // Color scheme
    private static final Color PRIMARY_COLOR = new Color(45, 52, 68);
    private static final Color ACCENT_COLOR = new Color(0, 123, 255);
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69);
    private static final Color DANGER_COLOR = new Color(220, 53, 69);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_COLOR = new Color(33, 37, 41);

    private ExamDAO examDAO;
    private CourseDAO courseDAO;
    private DefaultTableModel tableModel;
    private JTable examTable;
    private JTextField examNameField;
    private JTextField durationField;
    private JComboBox<Course> courseCombo;
    private JButton addButton, editButton, deleteButton, refreshButton;

    public ExamManagementPanel() {
        this.examDAO = new ExamDAO();
        this.courseDAO = new CourseDAO();

        initializePanel();
        loadExams();
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Create main components
        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createFormPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(new EmptyBorder(0, 0, 20, 0));

        JLabel titleLabel = new JLabel("📝 Examination Management");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(TEXT_COLOR);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(Color.WHITE);

        refreshButton = createStyledButton("🔄 Refresh", ACCENT_COLOR);
        refreshButton.addActionListener(e -> loadExams());

        buttonPanel.add(refreshButton);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(buttonPanel, BorderLayout.EAST);

        return headerPanel;
    }

    private JPanel createTablePanel() {
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(CARD_COLOR);
        tablePanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1),
                new EmptyBorder(15, 15, 15, 15)));

        // Create table
        String[] columns = { "ID", "Exam Name", "Course", "Duration", "Actions" };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Only actions column is editable
            }
        };

        examTable = new JTable(tableModel);
        examTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        examTable.setRowHeight(40);
        examTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        examTable.setGridColor(new Color(222, 226, 230));
        examTable.setShowVerticalLines(true);
        examTable.setShowHorizontalLines(true);

        // Custom renderer for actions column
        examTable.getColumn("Actions").setCellRenderer(new ActionsCellRenderer());
        examTable.getColumn("Actions").setCellEditor(new ActionsCellEditor());

        // Set column widths
        examTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        examTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        examTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        examTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        examTable.getColumnModel().getColumn(4).setPreferredWidth(150);

        JScrollPane scrollPane = new JScrollPane(examTable);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Color.WHITE);

        tablePanel.add(scrollPane, BorderLayout.CENTER);

        return tablePanel;
    }

    private JPanel createFormPanel() {
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(CARD_COLOR);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1),
                new EmptyBorder(20, 20, 20, 20)));

        // Form title
        JLabel formTitle = new JLabel("Add New Exam");
        formTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        formTitle.setForeground(TEXT_COLOR);
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        formPanel.add(formTitle);
        formPanel.add(Box.createVerticalStrut(15));

        // Form fields
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        fieldsPanel.setBackground(CARD_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Exam Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        fieldsPanel.add(new JLabel("Exam Name:"), gbc);
        gbc.gridx = 1;
        examNameField = new JTextField(20);
        examNameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fieldsPanel.add(examNameField, gbc);

        // Course
        gbc.gridx = 2;
        gbc.gridy = 0;
        fieldsPanel.add(new JLabel("Course:"), gbc);
        gbc.gridx = 3;
        courseCombo = new JComboBox<>();
        courseCombo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        loadCourses();
        fieldsPanel.add(courseCombo, gbc);

        // Duration
        gbc.gridx = 4;
        gbc.gridy = 0;
        fieldsPanel.add(new JLabel("Duration:"), gbc);
        gbc.gridx = 5;
        durationField = new JTextField(10);
        durationField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fieldsPanel.add(durationField, gbc);

        fieldsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(fieldsPanel);
        formPanel.add(Box.createVerticalStrut(15));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(CARD_COLOR);

        addButton = createStyledButton("➕ Add Exam", SUCCESS_COLOR);
        addButton.addActionListener(e -> addExam());

        editButton = createStyledButton("✏️ Update", ACCENT_COLOR);
        editButton.addActionListener(e -> updateExam());
        editButton.setEnabled(false);

        deleteButton = createStyledButton("🗑️ Delete", DANGER_COLOR);
        deleteButton.addActionListener(e -> deleteExam());
        deleteButton.setEnabled(false);

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);

        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(buttonPanel);

        return formPanel;
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 16, 8, 16));

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

    private void loadCourses() {
        courseCombo.removeAllItems();
        List<Course> courses = courseDAO.getAllCourses();
        for (Course course : courses) {
            courseCombo.addItem(course);
        }
    }

    private void loadExams() {
        tableModel.setRowCount(0);
        List<Exam> exams = examDAO.getAllExams();

        for (Exam exam : exams) {
            Course course = courseDAO.getCourseById(exam.getCourseId());
            String courseName = course != null ? course.getName() : "Unknown";

            Object[] row = {
                    exam.getId(),
                    exam.getExamName(),
                    courseName,
                    exam.getDuration(),
                    "Actions" // This will be replaced by buttons
            };
            tableModel.addRow(row);
        }
    }

    private void addExam() {
        String examName = examNameField.getText().trim();
        String duration = durationField.getText().trim();
        Course selectedCourse = (Course) courseCombo.getSelectedItem();

        if (examName.isEmpty() || duration.isEmpty() || selectedCourse == null) {
            JOptionPane.showMessageDialog(this, "Please fill all fields.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Exam exam = new Exam(examName, selectedCourse.getId(), duration);

        if (examDAO.addExam(exam)) {
            JOptionPane.showMessageDialog(this, "Exam added successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            clearForm();
            loadExams();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to add exam.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateExam() {
        int selectedRow = examTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select an exam to update.",
                    "Selection Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String examName = examNameField.getText().trim();
        String duration = durationField.getText().trim();
        Course selectedCourse = (Course) courseCombo.getSelectedItem();

        if (examName.isEmpty() || duration.isEmpty() || selectedCourse == null) {
            JOptionPane.showMessageDialog(this, "Please fill all fields.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int examId = (Integer) tableModel.getValueAt(selectedRow, 0);
        Exam exam = new Exam(examId, examName, selectedCourse.getId(), duration);

        if (examDAO.updateExam(exam)) {
            JOptionPane.showMessageDialog(this, "Exam updated successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            clearForm();
            loadExams();
            resetFormForAdd();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to update exam.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteExam() {
        int selectedRow = examTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select an exam to delete.",
                    "Selection Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int option = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this exam?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            int examId = (Integer) tableModel.getValueAt(selectedRow, 0);

            if (examDAO.deleteExam(examId)) {
                JOptionPane.showMessageDialog(this, "Exam deleted successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                loadExams();
                resetFormForAdd();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete exam.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void fillFormForEdit(int row) {
        int examId = (Integer) tableModel.getValueAt(row, 0);
        String examName = (String) tableModel.getValueAt(row, 1);
        String duration = (String) tableModel.getValueAt(row, 3);

        examNameField.setText(examName);
        durationField.setText(duration);

        // Set the correct course in combo box
        Exam exam = examDAO.getExamById(examId);
        if (exam != null) {
            Course examCourse = courseDAO.getCourseById(exam.getCourseId());
            if (examCourse != null) {
                courseCombo.setSelectedItem(examCourse);
            }
        }

        // Change button states
        addButton.setEnabled(false);
        editButton.setEnabled(true);
        deleteButton.setEnabled(true);
    }

    private void resetFormForAdd() {
        addButton.setEnabled(true);
        editButton.setEnabled(false);
        deleteButton.setEnabled(false);
        examTable.clearSelection();
    }

    private void clearForm() {
        examNameField.setText("");
        durationField.setText("");
        courseCombo.setSelectedIndex(0);
    }

    // Custom cell renderer for actions column
    private class ActionsCellRenderer extends JPanel implements TableCellRenderer {
        private JButton editBtn;
        private JButton deleteBtn;

        public ActionsCellRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 5, 2));
            setBackground(Color.WHITE);

            editBtn = new JButton("Edit");
            editBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            editBtn.setBackground(ACCENT_COLOR);
            editBtn.setForeground(Color.WHITE);
            editBtn.setBorderPainted(false);
            editBtn.setFocusPainted(false);
            editBtn.setPreferredSize(new Dimension(50, 25));

            deleteBtn = new JButton("Del");
            deleteBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            deleteBtn.setBackground(DANGER_COLOR);
            deleteBtn.setForeground(Color.WHITE);
            deleteBtn.setBorderPainted(false);
            deleteBtn.setFocusPainted(false);
            deleteBtn.setPreferredSize(new Dimension(40, 25));

            add(editBtn);
            add(deleteBtn);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(Color.WHITE);
            }

            return this;
        }
    }

    // Custom cell editor for actions column
    private class ActionsCellEditor extends AbstractCellEditor implements TableCellEditor {
        private JPanel panel;
        private JButton editBtn;
        private JButton deleteBtn;
        private int currentRow;

        public ActionsCellEditor() {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
            panel.setBackground(Color.WHITE);

            editBtn = new JButton("Edit");
            editBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            editBtn.setBackground(ACCENT_COLOR);
            editBtn.setForeground(Color.WHITE);
            editBtn.setBorderPainted(false);
            editBtn.setFocusPainted(false);
            editBtn.setPreferredSize(new Dimension(50, 25));
            editBtn.addActionListener(e -> {
                fireEditingStopped();
                fillFormForEdit(currentRow);
            });

            deleteBtn = new JButton("Del");
            deleteBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            deleteBtn.setBackground(DANGER_COLOR);
            deleteBtn.setForeground(Color.WHITE);
            deleteBtn.setBorderPainted(false);
            deleteBtn.setFocusPainted(false);
            deleteBtn.setPreferredSize(new Dimension(40, 25));
            deleteBtn.addActionListener(e -> {
                fireEditingStopped();
                examTable.setRowSelectionInterval(currentRow, currentRow);
                deleteExam();
            });

            panel.add(editBtn);
            panel.add(deleteBtn);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            currentRow = row;
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return "Actions";
        }
    }
}