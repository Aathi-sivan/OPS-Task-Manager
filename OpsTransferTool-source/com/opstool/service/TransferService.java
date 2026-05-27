package com.opstool.service;

import com.opstool.model.Credential;
import com.opstool.model.ScheduledTask;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Executes file transfers via WinSCP's scripting interface (winscp.com).
 *
 * Credential resolution order for the TARGET system:
 *   1. Look up creds_<targetUsername>.xml in the data directory.
 *   2. Use the plain-text password stored there.
 *
 * Source system is auto-detected as the local machine (hostname + current OS user).
 * The source path comes directly from the task.
 */
public class TransferService {

    private static final String[] WINSCP_PATHS = {
        "C:\\Program Files (x86)\\WinSCP\\WinSCP.com",
        "C:\\Program Files\\WinSCP\\WinSCP.com",
        "winscp.com"
    };

    private final XmlStorageService storage;
    private String winScpPath;

    public TransferService(XmlStorageService storage) {
        this.storage   = storage;
        this.winScpPath = detectWinScp();
    }

    public void   setWinScpPath(String path) { this.winScpPath = path; }
    public String getWinScpPath()            { return winScpPath; }

    private String detectWinScp() {
        for (String p : WINSCP_PATHS) {
            if (new File(p).exists()) return p;
        }
        return WINSCP_PATHS[0];
    }

    // ─── Auto-detect source system ───────────────────────────────────────────

    /** Returns the local machine's hostname (source system). */
    public static String getLocalHostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return System.getenv("COMPUTERNAME"); }
    }

    /** Returns the OS user running this application (source system login). */
    public static String getLocalUsername() {
        return System.getProperty("user.name", System.getenv("USERNAME"));
    }

    // ─── File Transfer ───────────────────────────────────────────────────────

    /**
     * Executes a file transfer task.
     *
     * Target credentials are resolved from creds_<targetUsername>.xml.
     * Source system is the local machine; sourcePath comes from the task.
     */
    public boolean executeTransfer(ScheduledTask task, Consumer<String> logLine) {
        Credential target = resolveTargetCredential(task, logLine);
        if (target == null) return false;

        logLine.accept("[INFO] Source system : " + getLocalHostname() + " (local)");
        logLine.accept("[INFO] Source path   : " + task.getSourcePath());
        logLine.accept("[INFO] Target system : " + target.getHost());
        logLine.accept("[INFO] Target path   : " + task.getTargetPath());
        logLine.accept("[INFO] Target user   : " + target.getUsername());

        File scriptFile = null;
        try {
            scriptFile = buildWinScpScript(target, target.getPassword(), task, logLine);
            logLine.accept("[INFO] WinSCP script prepared. Starting transfer...");
            return runWinScpScript(scriptFile, logLine);
        } catch (Exception e) {
            logLine.accept("[ERROR] Transfer failed: " + e.getMessage());
            return false;
        } finally {
            if (scriptFile != null) scriptFile.delete();
        }
    }

    private File buildWinScpScript(Credential target, String password,
                                    ScheduledTask task, Consumer<String> logLine) throws IOException {
        File tmpScript = File.createTempFile("opstool_", ".txt");
        tmpScript.setReadable(false, false);
        tmpScript.setReadable(true, true);
        tmpScript.setWritable(false, false);
        tmpScript.setWritable(true, true);

        String sourcePath = task.getSourcePath().replace("\\", "/");
        String targetPath = task.getTargetPath().replace("\\", "/");

        logLine.accept("[INFO] Target OS: " + target.getOsType() + " | Protocol: SFTP");

        try (PrintWriter pw = new PrintWriter(new FileWriter(tmpScript))) {
            pw.println("option batch abort");
            pw.println("option confirm off");
            pw.println("open sftp://" + escapeUrl(target.getUsername())
                + ":" + escapeUrl(password) + "@" + target.getHost());
            if (sourcePath.endsWith("*") || new File(task.getSourcePath()).isDirectory()) {
                pw.println("synchronize remote \"" + sourcePath + "\" \"" + targetPath + "\"");
            } else {
                pw.println("put \"" + sourcePath + "\" \"" + targetPath + "\"");
            }
            pw.println("close");
            pw.println("exit");
        }
        return tmpScript;
    }

    private boolean runWinScpScript(File scriptFile, Consumer<String> logLine) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            winScpPath,
            "/script=" + scriptFile.getAbsolutePath(),
            "/log=" + getTempLogPath(),
            "/loglevel=1");
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                logLine.accept(maskPasswords(line));
            }
        }

        int exitCode = proc.waitFor();
        if (exitCode == 0) {
            logLine.accept("[SUCCESS] Transfer completed successfully.");
            return true;
        } else {
            logLine.accept("[ERROR] WinSCP exited with code: " + exitCode);
            return false;
        }
    }

    // ─── Service control ────────────────────────────────────────────────────

    public boolean executeServiceAction(ScheduledTask task, Consumer<String> logLine) {
        Credential target = resolveTargetCredential(task, logLine);
        if (target == null) return false;

        String action;
        switch (task.getTaskType()) {
            case START_SERVICE:   action = "Start-Service";   break;
            case STOP_SERVICE:    action = "Stop-Service";    break;
            case RESTART_SERVICE: action = "Restart-Service"; break;
            default:
                logLine.accept("[ERROR] Unknown service action.");
                return false;
        }

        logLine.accept("[INFO] Executing " + action + " on " + target.getHost()
            + " for service: " + task.getServiceName());

        String psCmd = String.format(
            "$pw = ConvertTo-SecureString '%s' -AsPlainText -Force; " +
            "$cred = New-Object System.Management.Automation.PSCredential('%s\\%s', $pw); " +
            "Invoke-Command -ComputerName '%s' -Credential $cred -ScriptBlock { %s -Name '%s' -Force }",
            target.getPassword().replace("'", "''"),
            target.getHost(), target.getUsername().replace("'", "''"),
            target.getHost(),
            action,
            task.getServiceName().replace("'", "''")
        );

        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe",
                "-NonInteractive", "-NoProfile", "-Command", psCmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    logLine.accept(maskPasswords(line));
                }
            }

            int exit = proc.waitFor();
            if (exit == 0) {
                logLine.accept("[SUCCESS] Service action completed.");
                return true;
            } else {
                logLine.accept("[ERROR] PowerShell remoting failed (exit " + exit + "). "
                    + "Ensure WinRM is enabled on the target and firewall allows port 5985.");
                return false;
            }
        } catch (Exception e) {
            logLine.accept("[ERROR] Failed to invoke PowerShell: " + e.getMessage());
            return false;
        }
    }

    // ─── Credential resolution ───────────────────────────────────────────────

    /**
     * Resolves the target credential for a task.
     *
     * Strategy:
     *  1. If the task has a targetUsername set, load creds_<username>.xml.
     *  2. Fall back to the old targetCredentialId lookup for backward compatibility.
     */
    private Credential resolveTargetCredential(ScheduledTask task, Consumer<String> logLine) {
        // New per-user XML lookup
        String uname = task.getTargetUsername();
        if (uname != null && !uname.isEmpty()) {
            Credential c = storage.loadCredentialByUsername(uname);
            if (c != null) return c;
            logLine.accept("[ERROR] No credential file found for username '" + uname
                + "'. Expected file: " + storage.credFileForUser(uname).getName()
                + " in " + storage.getDataDir().getAbsolutePath());
            return null;
        }

        // Legacy: look up by targetCredentialId
        if (task.getTargetCredentialId() != null && !task.getTargetCredentialId().isEmpty()) {
            Credential c = storage.loadAllCredentials().stream()
                .filter(x -> x.getId().equals(task.getTargetCredentialId()))
                .findFirst().orElse(null);
            if (c != null) return c;
        }

        logLine.accept("[ERROR] Target credentials not configured for this task.");
        return null;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String escapeUrl(String s) {
        return s.replace(";", "%3B").replace("@", "%40");
    }

    private String maskPasswords(String line) {
        return line.replaceAll("(?i)(password[=:])\\S+", "$1****");
    }

    private String getTempLogPath() {
        return System.getProperty("java.io.tmpdir") + File.separator + "opstool_winscp.log";
    }
}
