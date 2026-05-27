package com.opstool.ui;

import com.opstool.model.Credential;
import com.opstool.model.ScheduledTask;
import com.opstool.service.TransferService;
import com.opstool.service.XmlStorageService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Simplified task dialog for the ops team.
 *
 * What ops staff enter:
 *   - Task name + type + schedule
 *   - Source path  (auto-filled with local machine, editable)
 *   - Destination folder on the target system
 *   - Target system hostname / IP
 *   - Target system username
 *   - Target system password
 *
 * The password is NOT stored in tasks.xml.  Instead, saving the task also
 * writes / updates creds_<username>.xml so the password can be retrieved
 * at run time.
 */
public class TaskDialog extends JDialog {

    private final XmlStorageService storage;
    private ScheduledTask result;

    // ── General ──────────────────────────────────────────────────────────────
    private JTextField  tfName;
    private JComboBox<String> cbTaskType;

    // ── Source (auto-detected, editable) ─────────────────────────────────────
    private JTextField  tfSourceHost;   // auto-filled: local hostname
    private JTextField  tfSourceUser;   // auto-filled: local OS user
    private JTextField  tfSourcePath;   // file / folder to transfer

    // ── Target system credentials ─────────────────────────────────────────────
    private JTextField  tfTargetHost;
    private JTextField  tfTargetUser;
    private JPasswordField pfTargetPass;
    private JComboBox<String> cbTargetOs;

    // ── File transfer extra ───────────────────────────────────────────────────
    private JTextField  tfTargetFolder;  // destination folder on target

    // ── Service control ───────────────────────────────────────────────────────
    private JTextField  tfServiceName;

    // ── Schedule ─────────────────────────────────────────────────────────────
    private JComboBox<String> cbScheduleType;
    private JTextField  tfScheduledAt;
    private JTextField  tfInterval;
    private JComboBox<String> cbDayOfWeek;
    private JTextField  tfTime;

    private JPanel fileTransferPanel;
    private JPanel servicePanel;
    private JPanel scheduleDetailsPanel;

    public TaskDialog(Frame parent, XmlStorageService storage, ScheduledTask existing) {
        super(parent, existing == null ? "New Task" : "Edit Task", true);
        this.storage = storage;
        setSize(560, 680);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));
        buildUI(existing);
    }

    private void buildUI(ScheduledTask existing) {
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(new EmptyBorder(12, 15, 5, 15));

        // ── General ──────────────────────────────────────────────────────────
        JPanel general = titledPanel("General");
        tfName     = new JTextField(existing != null ? existing.getName() : "", 28);
        cbTaskType = new JComboBox<>(new String[]{
            "FILE_TRANSFER", "START_SERVICE", "STOP_SERVICE", "RESTART_SERVICE"});
        if (existing != null) cbTaskType.setSelectedItem(existing.getTaskType().name());

        addRow(general, "Task Name *",  tfName,     0);
        addRow(general, "Task Type *",  cbTaskType, 1);
        main.add(general);

        // ── Source system (auto-detected, editable) ───────────────────────────
        JPanel sourcePanel = titledPanel("Source System  (auto-detected — editable if needed)");
        tfSourceHost = new JTextField(TransferService.getLocalHostname(), 28);
        tfSourceUser = new JTextField(TransferService.getLocalUsername(), 28);
        tfSourcePath = new JTextField(
            existing != null && existing.getSourcePath() != null ? existing.getSourcePath() : "", 28);

        tfSourceHost.setForeground(new Color(0x555555));
        tfSourceUser.setForeground(new Color(0x555555));

        addRow(sourcePanel, "Hostname / IP",  tfSourceHost, 0);
        addRow(sourcePanel, "Username",       tfSourceUser, 1);
        addRow(sourcePanel, "Source Path *",  tfSourcePath, 2);
        addRow(sourcePanel, "",
            hint("Local file or folder, e.g.  C:\\data\\report.csv  or  C:\\exports\\*"),
            3);
        main.add(sourcePanel);

        // ── File Transfer panel ───────────────────────────────────────────────
        fileTransferPanel = titledPanel("File Transfer — Destination");
        tfTargetFolder = new JTextField(
            existing != null && existing.getTargetPath() != null ? existing.getTargetPath() : "", 28);
        addRow(fileTransferPanel, "Destination Folder *", tfTargetFolder, 0);
        addRow(fileTransferPanel, "",
            hint("Must already exist on the target, e.g.  /opt/uploads/  or  C:\\Incoming\\"),
            1);
        main.add(fileTransferPanel);

        // ── Service panel ─────────────────────────────────────────────────────
        servicePanel = titledPanel("Service Control");
        tfServiceName = new JTextField(
            existing != null && existing.getServiceName() != null ? existing.getServiceName() : "", 28);
        addRow(servicePanel, "Service Name *", tfServiceName, 0);
        addRow(servicePanel, "", hint("Windows service name, e.g.  Spooler,  W3SVC"), 1);
        main.add(servicePanel);

        // ── Target credentials ────────────────────────────────────────────────
        JPanel targetPanel = titledPanel("Target System Credentials");

        // Pre-fill if task has a targetUsername and a creds file exists
        String existingUser = existing != null ? existing.getTargetUsername() : null;
        Credential existingCred = existingUser != null
            ? storage.loadCredentialByUsername(existingUser) : null;

        tfTargetHost  = new JTextField(existingCred != null ? existingCred.getHost() : "", 28);
        tfTargetUser  = new JTextField(existingUser != null ? existingUser : "", 28);
        pfTargetPass  = new JPasswordField(28);
        cbTargetOs    = new JComboBox<>(new String[]{"WINDOWS", "LINUX"});

        if (existingCred != null) {
            // Show placeholder so user knows a password is already stored
            pfTargetPass.setText(existingCred.getPassword() != null ? existingCred.getPassword() : "");
            cbTargetOs.setSelectedItem(existingCred.getOsType());
        }

        addRow(targetPanel, "Hostname / IP *",  tfTargetHost, 0);
        addRow(targetPanel, "Username *",        tfTargetUser, 1);
        addRow(targetPanel, "Password *",        pfTargetPass, 2);
        addRow(targetPanel, "OS Type *",         cbTargetOs,   3);
        addRow(targetPanel, "",
            hint("Password is saved in plain text in creds_<username>.xml"), 4);
        main.add(targetPanel);

        // ── Schedule ──────────────────────────────────────────────────────────
        JPanel sched = titledPanel("Schedule");
        cbScheduleType = new JComboBox<>(new String[]{
            "RUN_NOW", "ONCE", "DAILY", "WEEKLY", "INTERVAL_MINUTES"});
        if (existing != null) cbScheduleType.setSelectedItem(existing.getScheduleType().name());

        scheduleDetailsPanel = new JPanel(new GridBagLayout());
        tfScheduledAt = new JTextField(LocalDateTime.now().plusMinutes(5)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), 18);
        tfInterval    = new JTextField("30", 6);
        cbDayOfWeek   = new JComboBox<>(new String[]{
            "MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"});
        tfTime        = new JTextField("09:00", 8);

        if (existing != null) {
            if (existing.getScheduledAt() != null)
                tfScheduledAt.setText(
                    existing.getScheduledAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            tfInterval.setText(String.valueOf(existing.getIntervalMinutes()));
            if (existing.getCronExpression() != null && !existing.getCronExpression().isEmpty()) {
                String[] parts = existing.getCronExpression().split(" ");
                if (parts.length >= 2) { cbDayOfWeek.setSelectedItem(parts[0]); tfTime.setText(parts[1]); }
                else                  { tfTime.setText(existing.getCronExpression()); }
            }
        }

        addRow(sched, "Schedule Type *", cbScheduleType, 0);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 1; gc.gridwidth = 2;
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        gc.insets = new Insets(4, 4, 4, 4);
        sched.add(scheduleDetailsPanel, gc);
        main.add(sched);

        // ── Wire listeners ────────────────────────────────────────────────────
        cbTaskType.addActionListener(e -> updateVisibility());
        cbScheduleType.addActionListener(e -> updateScheduleDetails());
        updateVisibility();
        updateScheduleDetails();

        JScrollPane scroll = new JScrollPane(main);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        // ── Buttons ───────────────────────────────────────────────────────────
        JButton btnSave   = new JButton(existing == null ? "Create Task" : "Save Changes");
        JButton btnCancel = new JButton("Cancel");
        styleBtn(btnSave, new Color(0x1565C0));
        btnCancel.addActionListener(e -> dispose());
        btnSave.addActionListener(e -> save(existing));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.setBorder(new EmptyBorder(0, 10, 10, 10));
        btnRow.add(btnCancel);
        btnRow.add(btnSave);
        add(btnRow, BorderLayout.SOUTH);
    }

    // ── Visibility helpers ────────────────────────────────────────────────────

    private void updateVisibility() {
        boolean isTransfer = "FILE_TRANSFER".equals(cbTaskType.getSelectedItem());
        fileTransferPanel.setVisible(isTransfer);
        servicePanel.setVisible(!isTransfer);
        revalidate(); repaint();
    }

    private void updateScheduleDetails() {
        scheduleDetailsPanel.removeAll();
        String stype = (String) cbScheduleType.getSelectedItem();
        switch (stype) {
            case "RUN_NOW":
                scheduleDetailsPanel.add(
                    hint("Task will execute on the next scheduler tick (~60 s)"),
                    fullRow(0));
                break;
            case "ONCE":
                addRowTo(scheduleDetailsPanel, "Run at (yyyy-MM-dd HH:mm) *", tfScheduledAt, 0);
                break;
            case "DAILY":
                addRowTo(scheduleDetailsPanel, "Time (HH:mm) *", tfTime, 0);
                break;
            case "WEEKLY":
                addRowTo(scheduleDetailsPanel, "Day *",           cbDayOfWeek, 0);
                addRowTo(scheduleDetailsPanel, "Time (HH:mm) *",  tfTime,      1);
                break;
            case "INTERVAL_MINUTES":
                addRowTo(scheduleDetailsPanel, "Every N minutes *", tfInterval, 0);
                break;
        }
        scheduleDetailsPanel.revalidate();
        scheduleDetailsPanel.repaint();
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    private void save(ScheduledTask existing) {
        // ── Validate ──────────────────────────────────────────────────────────
        String name = tfName.getText().trim();
        if (name.isEmpty()) { msg("Task name is required."); return; }

        String targetHost = tfTargetHost.getText().trim();
        String targetUser = tfTargetUser.getText().trim();
        String targetPass = new String(pfTargetPass.getPassword());

        if (targetHost.isEmpty()) { msg("Target hostname / IP is required."); return; }
        if (targetUser.isEmpty()) { msg("Target username is required.");      return; }
        if (targetPass.isEmpty()) { msg("Target password is required.");      return; }

        boolean isTransfer = "FILE_TRANSFER".equals(cbTaskType.getSelectedItem());
        if (isTransfer) {
            if (tfSourcePath.getText().trim().isEmpty())  { msg("Source path is required.");       return; }
            if (tfTargetFolder.getText().trim().isEmpty()) { msg("Destination folder is required."); return; }
        } else {
            if (tfServiceName.getText().trim().isEmpty()) { msg("Service name is required."); return; }
        }

        // ── Persist credential file creds_<username>.xml ──────────────────────
        Credential cred = storage.loadCredentialByUsername(targetUser);
        if (cred == null) cred = new Credential();
        if (cred.getId() == null) cred.setId(UUID.randomUUID().toString());
        cred.setName(targetUser + "@" + targetHost);
        cred.setHost(targetHost);
        cred.setUsername(targetUser);
        cred.setPassword(targetPass);
        cred.setOsType((String) cbTargetOs.getSelectedItem());
        storage.saveCredential(cred);

        // ── Build task ────────────────────────────────────────────────────────
        ScheduledTask t = existing != null ? existing : new ScheduledTask();
        if (t.getId() == null) t.setId(UUID.randomUUID().toString());
        t.setName(name);
        t.setTaskType(ScheduledTask.TaskType.valueOf((String) cbTaskType.getSelectedItem()));
        t.setStatus(ScheduledTask.TaskStatus.PENDING);
        t.setTargetUsername(targetUser);
        // Store source host/user in the sourceCredentialId slot (informational)
        t.setSourceCredentialId(tfSourceUser.getText().trim() + "@" + tfSourceHost.getText().trim());

        if (isTransfer) {
            t.setSourcePath(tfSourcePath.getText().trim());
            t.setTargetPath(tfTargetFolder.getText().trim());
        } else {
            t.setServiceName(tfServiceName.getText().trim());
        }

        // ── Schedule ──────────────────────────────────────────────────────────
        String stype = (String) cbScheduleType.getSelectedItem();
        t.setScheduleType(ScheduledTask.ScheduleType.valueOf(stype));
        try {
            switch (stype) {
                case "ONCE":
                    t.setScheduledAt(LocalDateTime.parse(tfScheduledAt.getText().trim(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    break;
                case "DAILY":
                    t.setCronExpression(tfTime.getText().trim());
                    break;
                case "WEEKLY":
                    t.setCronExpression(cbDayOfWeek.getSelectedItem() + " " + tfTime.getText().trim());
                    break;
                case "INTERVAL_MINUTES":
                    t.setIntervalMinutes(Integer.parseInt(tfInterval.getText().trim()));
                    break;
            }
        } catch (Exception ex) {
            msg("Invalid schedule values: " + ex.getMessage()); return;
        }

        storage.saveTask(t);
        result = t;
        dispose();
    }

    public ScheduledTask getResult() { return result; }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private JPanel titledPanel(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder(title));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return p;
    }

    private void addRow(JPanel p, String label, JComponent field, int row) {
        GridBagConstraints lc = labelGbc(row);
        GridBagConstraints fc = fieldGbc(row);
        p.add(new JLabel(label), lc);
        p.add(field, fc);
    }

    private void addRowTo(JPanel p, String label, JComponent field, int row) {
        addRow(p, label, field, row);
    }

    private JLabel hint(String text) {
        JLabel l = new JLabel("<html><i style='color:gray'>" + text + "</i></html>");
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        return l;
    }

    private GridBagConstraints labelGbc(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 8);
        return c;
    }

    private GridBagConstraints fieldGbc(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1; c.gridy = row;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(4, 0, 4, 4);
        return c;
    }

    private GridBagConstraints fullRow(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        c.insets = new Insets(4, 4, 4, 4);
        return c;
    }

    private void styleBtn(JButton b, Color bg) {
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFocusPainted(false); b.setOpaque(true); b.setBorderPainted(false);
    }

    private void msg(String text) {
        JOptionPane.showMessageDialog(this, text);
    }
}
