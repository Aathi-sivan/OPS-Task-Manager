package com.opstool.ui;

import com.opstool.model.ScheduledTask;
import com.opstool.service.TaskSchedulerService;
import com.opstool.service.XmlStorageService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskManagerPanel extends JPanel {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final XmlStorageService storage;
    private final TaskSchedulerService scheduler;
    private DefaultTableModel tableModel;
    private JTable table;
    private JTextArea logArea;
    private JLabel lblSelectedTask;

    // taskId -> accumulated log
    private final Map<String, StringBuilder> taskLogs = new HashMap<>();

    public TaskManagerPanel(XmlStorageService storage, TaskSchedulerService scheduler) {
        this.storage = storage;
        this.scheduler = scheduler;
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        buildUI();

        // Register log callback
        scheduler.setLogCallback((taskId, line) -> SwingUtilities.invokeLater(() -> appendLog(taskId, line)));
    }

    private void buildUI() {
        // ── Toolbar ──────────────────────────────────────────────────────────
        JButton btnNew = new JButton("New Task");
        JButton btnEdit = new JButton("Edit");
        JButton btnDelete = new JButton("Delete");
        JButton btnRunNow = new JButton("▶ Run Now");
        JButton btnEnable = new JButton("Enable/Disable");
        JButton btnRefresh = new JButton("⟳ Refresh");

        styleBtn(btnNew, new Color(0x2E7D32));
        styleBtn(btnRunNow, new Color(0x1565C0));
        styleBtn(btnDelete, new Color(0xC62828));

        btnNew.addActionListener(e -> newTask());
        btnEdit.addActionListener(e -> editTask());
        btnDelete.addActionListener(e -> deleteTask());
        btnRunNow.addActionListener(e -> runNow());
        btnEnable.addActionListener(e -> toggleEnable());
        btnRefresh.addActionListener(e -> refresh());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolbar.add(btnNew); toolbar.add(btnEdit); toolbar.add(btnDelete);
        toolbar.add(new JSeparator(JSeparator.VERTICAL));
        toolbar.add(btnRunNow); toolbar.add(btnEnable);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(btnRefresh);

        // ── Table ────────────────────────────────────────────────────────────
        String[] cols = {"Name", "Type", "Status", "Schedule", "Last Run", "Last Result"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                Component c = super.prepareRenderer(renderer, row, col);
                String status = (String) tableModel.getValueAt(row, 2);
                if (!isRowSelected(row)) {
                    if ("RUNNING".equals(status)) c.setBackground(new Color(0xE3F2FD));
                    else if ("FAILED".equals(status)) c.setBackground(new Color(0xFFEBEE));
                    else if ("SUCCESS".equals(status)) c.setBackground(new Color(0xE8F5E9));
                    else if ("DISABLED".equals(status)) c.setBackground(new Color(0xF5F5F5));
                    else c.setBackground(Color.WHITE);
                }
                return c;
            }
        };
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.getColumnModel().getColumn(4).setPreferredWidth(130);
        table.getColumnModel().getColumn(5).setPreferredWidth(90);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showLogForSelected();
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(800, 220));

        // ── Log panel ────────────────────────────────────────────────────────
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(0x1E1E1E));
        logArea.setForeground(new Color(0xD4D4D4));
        logArea.setCaretColor(Color.WHITE);
        JScrollPane logScroll = new JScrollPane(logArea);

        lblSelectedTask = new JLabel("Select a task to view its execution log");
        lblSelectedTask.setFont(lblSelectedTask.getFont().deriveFont(Font.BOLD));
        lblSelectedTask.setBorder(new EmptyBorder(4, 2, 4, 0));

        JButton btnClearLog = new JButton("Clear Log");
        btnClearLog.addActionListener(e -> {
            logArea.setText("");
            int row = table.getSelectedRow();
            if (row >= 0) {
                String id = (String) table.getClientProperty("id_" + row);
                if (id != null) taskLogs.remove(id);
            }
        });

        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.add(lblSelectedTask, BorderLayout.WEST);
        logHeader.add(btnClearLog, BorderLayout.EAST);

        JPanel logPanel = new JPanel(new BorderLayout(4, 4));
        logPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        logPanel.add(logHeader, BorderLayout.NORTH);
        logPanel.add(logScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, logPanel);
        split.setResizeWeight(0.45);
        split.setBorder(null);

        add(toolbar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        refresh();
    }

    public void refresh() {
        int selectedRow = table.getSelectedRow();
        String selectedId = selectedRow >= 0 ? (String) table.getClientProperty("id_" + selectedRow) : null;

        tableModel.setRowCount(0);
        List<ScheduledTask> tasks = storage.loadTasks();
        for (int i = 0; i < tasks.size(); i++) {
            ScheduledTask t = tasks.get(i);
            String schedDesc = buildScheduleDescription(t);
            tableModel.addRow(new Object[]{
                t.getName(),
                t.getTaskType().name().replace("_", " "),
                t.getStatus().name(),
                schedDesc,
                t.getLastRunAt() != null ? t.getLastRunAt().format(DT) : "Never",
                t.getLastRunResult() != null ? t.getLastRunResult() : ""
            });
            table.putClientProperty("id_" + i, t.getId());
        }

        // Re-select previous row
        if (selectedId != null) {
            for (int i = 0; i < tasks.size(); i++) {
                if (selectedId.equals(tasks.get(i).getId())) {
                    table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    private String buildScheduleDescription(ScheduledTask t) {
        switch (t.getScheduleType()) {
            case RUN_NOW: return "Run Now";
            case ONCE: return "Once @ " + (t.getScheduledAt() != null ? t.getScheduledAt().format(DT) : "?");
            case DAILY: return "Daily @ " + (t.getCronExpression() != null ? t.getCronExpression() : "?");
            case WEEKLY: return "Weekly " + (t.getCronExpression() != null ? t.getCronExpression() : "?");
            case INTERVAL_MINUTES: return "Every " + t.getIntervalMinutes() + " min";
            default: return "?";
        }
    }

    private void newTask() {
        TaskDialog dlg = new TaskDialog((Frame) SwingUtilities.getWindowAncestor(this), storage, null);
        dlg.setVisible(true);
        if (dlg.getResult() != null) refresh();
    }

    private void editTask() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a task to edit."); return; }
        String id = (String) table.getClientProperty("id_" + row);
        storage.loadTasks().stream().filter(t -> t.getId().equals(id)).findFirst().ifPresent(t -> {
            TaskDialog dlg = new TaskDialog((Frame) SwingUtilities.getWindowAncestor(this), storage, t);
            dlg.setVisible(true);
            if (dlg.getResult() != null) refresh();
        });
    }

    private void deleteTask() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a task to delete."); return; }
        String name = (String) tableModel.getValueAt(row, 0);
        String id = (String) table.getClientProperty("id_" + row);
        int ok = JOptionPane.showConfirmDialog(this, "Delete task \"" + name + "\"?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            storage.deleteTask(id);
            taskLogs.remove(id);
            refresh();
        }
    }

    private void runNow() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a task to run."); return; }
        String id = (String) table.getClientProperty("id_" + row);
        String name = (String) tableModel.getValueAt(row, 0);
        int ok = JOptionPane.showConfirmDialog(this,
            "Run task \"" + name + "\" immediately?", "Confirm Run Now",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            scheduler.runNow(id);
            JOptionPane.showMessageDialog(this, "Task \"" + name + "\" queued for immediate execution.");
            refresh();
        }
    }

    private void toggleEnable() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a task."); return; }
        String id = (String) table.getClientProperty("id_" + row);
        storage.loadTasks().stream().filter(t -> t.getId().equals(id)).findFirst().ifPresent(t -> {
            if (t.getStatus() == ScheduledTask.TaskStatus.DISABLED) {
                t.setStatus(ScheduledTask.TaskStatus.PENDING);
            } else {
                t.setStatus(ScheduledTask.TaskStatus.DISABLED);
            }
            storage.saveTask(t);
            refresh();
        });
    }

    private void showLogForSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            lblSelectedTask.setText("Select a task to view its execution log");
            logArea.setText("");
            return;
        }
        String name = (String) tableModel.getValueAt(row, 0);
        String id = (String) table.getClientProperty("id_" + row);
        lblSelectedTask.setText("Execution log: " + name);
        StringBuilder sb = taskLogs.getOrDefault(id, new StringBuilder());
        logArea.setText(sb.length() > 0 ? sb.toString() : "No log entries yet. Run the task to see output here.");
        logArea.setCaretPosition(logArea.getText().length());
    }

    private void appendLog(String taskId, String line) {
        taskLogs.computeIfAbsent(taskId, k -> new StringBuilder()).append(line).append("\n");
        int row = table.getSelectedRow();
        if (row >= 0) {
            String selectedId = (String) table.getClientProperty("id_" + row);
            if (taskId.equals(selectedId)) {
                logArea.append(line + "\n");
                logArea.setCaretPosition(logArea.getText().length());
            }
        }
        // Also refresh table status colors
        refresh();
    }

    private void styleBtn(JButton b, Color bg) {
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFocusPainted(false); b.setOpaque(true); b.setBorderPainted(false);
    }
}
