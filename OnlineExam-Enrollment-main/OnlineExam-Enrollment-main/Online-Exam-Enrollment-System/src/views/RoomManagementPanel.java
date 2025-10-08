package views;

import dao.*;
import models.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class RoomManagementPanel extends JPanel {

    // Color scheme
    private static final Color ACCENT_COLOR = new Color(0, 123, 255);
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69);
    private static final Color DANGER_COLOR = new Color(220, 53, 69);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_COLOR = new Color(33, 37, 41);

    private RoomDAO roomDAO;
    private DefaultTableModel tableModel;
    private JTable roomTable;
    private JTextField roomNameField;
    private JTextField capacityField;
    private JButton addButton, editButton, deleteButton, refreshButton;

    public RoomManagementPanel() {
        this.roomDAO = new RoomDAO();

        initializePanel();
        loadRooms();
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

        JLabel titleLabel = new JLabel("🏢 Room Management");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(TEXT_COLOR);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(Color.WHITE);

        refreshButton = createStyledButton("🔄 Refresh", ACCENT_COLOR);
        refreshButton.addActionListener(e -> loadRooms());

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
        String[] columns = { "ID", "Room Name", "Capacity", "Status", "Actions" };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Only actions column is editable
            }
        };

        roomTable = new JTable(tableModel);
        roomTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        roomTable.setRowHeight(40);
        roomTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomTable.setGridColor(new Color(222, 226, 230));
        roomTable.setShowVerticalLines(true);
        roomTable.setShowHorizontalLines(true);

        // Custom renderer for actions column
        roomTable.getColumn("Actions").setCellRenderer(new ActionsCellRenderer());
        roomTable.getColumn("Actions").setCellEditor(new ActionsCellEditor());

        // Set column widths
        roomTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        roomTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        roomTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        roomTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        roomTable.getColumnModel().getColumn(4).setPreferredWidth(150);

        JScrollPane scrollPane = new JScrollPane(roomTable);
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
        JLabel formTitle = new JLabel("Add New Room");
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

        // Room Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        fieldsPanel.add(new JLabel("Room Name:"), gbc);
        gbc.gridx = 1;
        roomNameField = new JTextField(20);
        roomNameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fieldsPanel.add(roomNameField, gbc);

        // Capacity
        gbc.gridx = 2;
        gbc.gridy = 0;
        fieldsPanel.add(new JLabel("Capacity:"), gbc);
        gbc.gridx = 3;
        capacityField = new JTextField(10);
        capacityField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fieldsPanel.add(capacityField, gbc);

        fieldsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(fieldsPanel);
        formPanel.add(Box.createVerticalStrut(15));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(CARD_COLOR);

        addButton = createStyledButton("➕ Add Room", SUCCESS_COLOR);
        addButton.addActionListener(e -> addRoom());

        editButton = createStyledButton("✏️ Update", ACCENT_COLOR);
        editButton.addActionListener(e -> updateRoom());
        editButton.setEnabled(false);

        deleteButton = createStyledButton("🗑️ Delete", DANGER_COLOR);
        deleteButton.addActionListener(e -> deleteRoom());
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

    private void loadRooms() {
        tableModel.setRowCount(0);
        List<Room> rooms = roomDAO.getAllRooms();

        for (Room room : rooms) {
            String status = room.getCapacity() > 50 ? "Large" : room.getCapacity() > 20 ? "Medium" : "Small";

            Object[] row = {
                    room.getId(),
                    room.getRoomName(),
                    room.getCapacity(),
                    status,
                    "Actions" // This will be replaced by buttons
            };
            tableModel.addRow(row);
        }
    }

    private void addRoom() {
        String roomName = roomNameField.getText().trim();
        String capacityText = capacityField.getText().trim();

        if (roomName.isEmpty() || capacityText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int capacity = Integer.parseInt(capacityText);
            if (capacity <= 0) {
                JOptionPane.showMessageDialog(this, "Capacity must be a positive number.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Room room = new Room(roomName, capacity);

            if (roomDAO.addRoom(room)) {
                JOptionPane.showMessageDialog(this, "Room added successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                clearForm();
                loadRooms();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to add room.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Capacity must be a valid number.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void updateRoom() {
        int selectedRow = roomTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a room to update.",
                    "Selection Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String roomName = roomNameField.getText().trim();
        String capacityText = capacityField.getText().trim();

        if (roomName.isEmpty() || capacityText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int capacity = Integer.parseInt(capacityText);
            if (capacity <= 0) {
                JOptionPane.showMessageDialog(this, "Capacity must be a positive number.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int roomId = (Integer) tableModel.getValueAt(selectedRow, 0);
            Room room = new Room(roomId, roomName, capacity);

            if (roomDAO.updateRoom(room)) {
                JOptionPane.showMessageDialog(this, "Room updated successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                clearForm();
                loadRooms();
                resetFormForAdd();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to update room.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Capacity must be a valid number.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void deleteRoom() {
        int selectedRow = roomTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a room to delete.",
                    "Selection Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int option = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this room?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            int roomId = (Integer) tableModel.getValueAt(selectedRow, 0);

            if (roomDAO.deleteRoom(roomId)) {
                JOptionPane.showMessageDialog(this, "Room deleted successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                loadRooms();
                resetFormForAdd();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete room.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void fillFormForEdit(int row) {
        String roomName = (String) tableModel.getValueAt(row, 1);
        int capacity = (Integer) tableModel.getValueAt(row, 2);

        roomNameField.setText(roomName);
        capacityField.setText(String.valueOf(capacity));

        // Change button states
        addButton.setEnabled(false);
        editButton.setEnabled(true);
        deleteButton.setEnabled(true);
    }

    private void resetFormForAdd() {
        addButton.setEnabled(true);
        editButton.setEnabled(false);
        deleteButton.setEnabled(false);
        roomTable.clearSelection();
    }

    private void clearForm() {
        roomNameField.setText("");
        capacityField.setText("");
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
                roomTable.setRowSelectionInterval(currentRow, currentRow);
                deleteRoom();
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