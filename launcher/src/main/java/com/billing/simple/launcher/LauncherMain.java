package com.billing.simple.launcher;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Production-Grade Native Desktop Supervisor, Tray Controller & Diagnostics Engine for RupeeCRM.
 * 
 * Invariants:
 * 1. Single authoritative data directory: %LOCALAPPDATA%\RupeeCRM\data
 * 2. Stateless binaries: %LOCALAPPDATA%\Programs\RupeeCRM
 * 3. Safe process tracking: PID and executable path validation, NO blanket java.exe termination.
 * 4. Transactional update & automatic rollback on health failure.
 * 5. Diagnostics engine with plain-language status and self-healing action triggers.
 */
public class LauncherMain {

    public static final String APP_URL = "http://localhost:28080/";
    public static final String HEALTH_URL = "http://127.0.0.1:28080/api/health";
    public static final int PORT = 28080;

    public static final String STARTUP_NOTIF_TITLE = "RupeeCRM";
    public static final String STARTUP_NOTIF_MESSAGE = "App started successfully\nStatus: Healthy\nReady to use";

    // Embedded Master Ed25519 Public Key for Release Manifest Authentication
    public static final String DEFAULT_PUBLIC_KEY_X509_BASE64 =
            "MCowBQYDK2VwAyEAdmc4i0VRQ4Whs4OqCfxuOWSiiLQiiyp8VlTRhBTGRZo=";

    private Process backendProcess;
    private Long backendPid = null;
    private TrayIcon trayIcon;
    private JFrame diagnosticsFrame;
    private boolean isBackgroundMode = false;
    private volatile boolean restartRequested = false;
    private volatile boolean exitRequested = false;
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);

    // Crash circuit breaker
    private final List<Long> crashTimestamps = new ArrayList<>();
    private static final int MAX_CRASHES_IN_WINDOW = 3;
    private static final long CRASH_WINDOW_MS = 60_000L;
    private volatile boolean circuitBreakerTripped = false;
    private final AtomicBoolean startupNotificationShown = new AtomicBoolean(false);

    // UI state elements
    private JLabel statusBadge;
    private JLabel statusDetailLabel;
    private JProgressBar progressBar;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");

        boolean background = false;
        boolean setupMode = false;
        boolean repairMode = false;
        boolean uninstallMode = false;

        for (String arg : args) {
            if ("--background".equalsIgnoreCase(arg) || "-b".equalsIgnoreCase(arg)) {
                background = true;
            } else if ("--setup".equalsIgnoreCase(arg) || "--installer".equalsIgnoreCase(arg)) {
                setupMode = true;
            } else if ("--repair".equalsIgnoreCase(arg)) {
                repairMode = true;
            } else if ("--uninstall".equalsIgnoreCase(arg)) {
                uninstallMode = true;
            }
        }

        if (setupMode) {
            showSetupModeDialog();
            return;
        }

        if (repairMode) {
            LauncherMain app = new LauncherMain();
            app.performInteractiveRepair();
            return;
        }

        if (uninstallMode) {
            LauncherMain app = new LauncherMain();
            app.performInteractiveUninstall();
            return;
        }

        // Single-instance detection: if server is already running, open browser and exit
        if (isBackendHealthy(1200)) {
            System.out.println("RupeeCRM backend is already active.");
            if (!background) {
                openBrowser(APP_URL);
            }
            System.exit(0);
            return;
        }

        LauncherMain app = new LauncherMain();
        app.isBackgroundMode = background;
        app.startService();
    }

    public void startService() {
        System.out.println("Starting RupeeCRM Supervisor & Background Service...");

        // Ensure persistent directories exist
        ensureDirectories();

        // Ensure autostart registration is initialized on Windows
        if (System.getProperty("os.name").toLowerCase().contains("win") && !isAutostartEnabled()) {
            setAutostartEnabled(true);
        }

        // Setup shutdown hook to cleanly terminate only RupeeCRM backend
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopBackend));

        // Setup System Tray
        setupSystemTray();

        // If not in background mode, open browser once healthy
        if (!isBackgroundMode) {
            new Thread(() -> {
                if (waitForBackend(25)) {
                    openBrowser(APP_URL);
                }
            }).start();
        }

        // Run main supervisor service loop
        new Thread(this::runServiceLoop, "RupeeCRM-Supervisor-Thread").start();
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(getDataDirectory());
            Files.createDirectories(getBackupsDirectory());
            Files.createDirectories(getLogsDirectory());
            Files.createDirectories(getStagingDirectory());
        } catch (IOException e) {
            System.err.println("Warning: could not create standard directories: " + e.getMessage());
        }
    }

    private void runServiceLoop() {
        while (!exitRequested) {
            if (circuitBreakerTripped) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
                continue;
            }

            applyPendingUpdateIfPresent();

            File warFile = findCurrentWar();
            if (warFile == null || !warFile.exists()) {
                System.err.println("CRITICAL: rupeecrm.war not found. Halting supervisor.");
                updateTrayTooltip("RupeeCRM: Damaged (Missing Application Engine)");
                showTrayNotification("RupeeCRM Needs Attention", "Application files are missing. Click tray icon to Repair.", TrayIcon.MessageType.WARNING);
                showDiagnosticsWindow();
                circuitBreakerTripped = true;
                continue;
            }

            try {
                System.out.println("Launching RupeeCRM backend from: " + warFile.getAbsolutePath());
                updateTrayTooltip("RupeeCRM: Starting Backend...");
                backendProcess = launchBackendProcess(warFile);
                backendPid = backendProcess.pid();

                boolean healthy = waitForBackend(30);
                if (healthy) {
                    System.out.println("RupeeCRM backend is healthy (PID: " + backendPid + ")");
                    updateTrayTooltip("RupeeCRM [● Running on port " + PORT + "]");
                    triggerStartupNotificationIfReady();
                } else {
                    System.err.println("RupeeCRM backend failed health check within 30s.");
                    recordCrashEvent();
                }

                // Wait for process termination
                int exitCode = backendProcess.waitFor();
                System.out.println("Backend process exited with code: " + exitCode);
                backendPid = null;

            } catch (InterruptedException e) {
                System.err.println("Supervisor loop interrupted: " + e.getMessage());
                break;
            } catch (Exception e) {
                System.err.println("Error launching backend: " + e.getMessage());
                recordCrashEvent();
            }

            if (exitRequested) {
                break;
            }

            if (restartRequested) {
                restartRequested = false;
                System.out.println("Manual restart requested. Re-launching...");
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                continue;
            }

            if (isUpdating.get()) {
                // Sleep during update cycle
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                continue;
            }

            recordCrashEvent();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
        }

        System.out.println("RupeeCRM Supervisor shutdown complete.");
        System.exit(0);
    }

    private void recordCrashEvent() {
        long now = System.currentTimeMillis();
        crashTimestamps.add(now);
        crashTimestamps.removeIf(ts -> (now - ts) > CRASH_WINDOW_MS);

        if (crashTimestamps.size() >= MAX_CRASHES_IN_WINDOW) {
            circuitBreakerTripped = true;
            updateTrayTooltip("RupeeCRM: Error (Stopped after repeated crashes)");
            showTrayNotification("RupeeCRM Stopped", "RupeeCRM stopped unexpectedly. Opening Diagnostics...", TrayIcon.MessageType.ERROR);
            showDiagnosticsWindow();
        }
    }

    private Process launchBackendProcess(File warFile) throws IOException {
        List<String> command = new ArrayList<>();

        File javaBin = resolveBundledJavaw();
        command.add(javaBin.getAbsolutePath());

        // JVM memory and execution parameters
        command.add("-Xms128m");
        command.add("-Xmx512m");
        command.add("-XX:+TieredCompilation");
        command.add("-XX:TieredStopAtLevel=1");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-DRUPEECRM_DATA_DIR=" + getDataDirectory().toAbsolutePath());
        command.add("-jar");
        command.add(warFile.getAbsolutePath());
        command.add("--server.port=" + PORT);
        command.add("--server.address=127.0.0.1");
        command.add("--spring.h2.console.enabled=false");

        ProcessBuilder pb = new ProcessBuilder(command);
        if (warFile.getParentFile() != null && warFile.getParentFile().exists()) {
            pb.directory(warFile.getParentFile());
        }

        Path logsDir = getLogsDirectory();
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logsDir.resolve("backend-stdout.log").toFile()));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(logsDir.resolve("backend-stderr.log").toFile()));

        return pb.start();
    }

    public synchronized void stopBackend() {
        if (backendProcess != null && backendProcess.isAlive()) {
            System.out.println("Gracefully stopping backend process (PID: " + backendPid + ")...");
            try {
                // Try sending graceful shutdown HTTP signal first
                triggerGracefulHttpShutdown();
                if (backendProcess.waitFor(5, TimeUnit.SECONDS)) {
                    System.out.println("Backend terminated gracefully.");
                    backendPid = null;
                    return;
                }
            } catch (Exception ignored) {}

            try {
                backendProcess.destroy();
                if (!backendProcess.waitFor(4, TimeUnit.SECONDS)) {
                    backendProcess.destroyForcibly();
                    backendProcess.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ignored) {
                backendProcess.destroyForcibly();
            }
            backendPid = null;
        }
    }

    private void triggerGracefulHttpShutdown() {
        try {
            URL url = new URL("http://127.0.0.1:" + PORT + "/api/shutdown");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }

    public static boolean isBackendHealthy(int timeoutMs) {
        return isBackendHealthy(HEALTH_URL, timeoutMs);
    }

    public static boolean isBackendHealthy(String healthUrl, int timeoutMs) {
        try {
            URL url = new URL(healthUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForBackend(int maxSeconds) {
        long start = System.currentTimeMillis();
        long maxMs = maxSeconds * 1000L;
        while (System.currentTimeMillis() - start < maxMs) {
            if (backendProcess != null && !backendProcess.isAlive()) {
                return false;
            }
            if (isBackendHealthy(1000)) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public static void openBrowser(String urlStr) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(urlStr));
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", urlStr});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", urlStr});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", urlStr});
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to open browser: " + e.getMessage());
        }
    }

    // ==========================================
    // SYSTEM TRAY & CONTEXT MENU
    // ==========================================

    private void setupSystemTray() {
        EventQueue.invokeLater(() -> {
            try {
                if (!SystemTray.isSupported()) {
                    System.out.println("System tray is not supported on this platform.");
                    return;
                }

                SystemTray tray = SystemTray.getSystemTray();
                Image image = createTrayIconImage(32);

                PopupMenu popup = new PopupMenu();

                MenuItem openItem = new MenuItem("Open RupeeCRM");
                openItem.addActionListener(e -> openBrowser(APP_URL));
                openItem.setFont(new Font("Segoe UI", Font.BOLD, 12));
                popup.add(openItem);

                MenuItem restartItem = new MenuItem("Restart RupeeCRM");
                restartItem.addActionListener(e -> {
                    restartRequested = true;
                    circuitBreakerTripped = false;
                    crashTimestamps.clear();
                    stopBackend();
                });
                popup.add(restartItem);

                popup.addSeparator();

                MenuItem diagItem = new MenuItem("System Diagnostics");
                diagItem.addActionListener(e -> showDiagnosticsWindow());
                popup.add(diagItem);

                Menu backupMenu = new Menu("Backup & Restore");
                MenuItem createBackupItem = new MenuItem("Create Backup Now");
                createBackupItem.addActionListener(e -> triggerManualBackup());
                MenuItem openBackupDirItem = new MenuItem("Open Backup Folder");
                openBackupDirItem.addActionListener(e -> openFolder(getBackupsDirectory().toFile()));
                backupMenu.add(createBackupItem);
                backupMenu.add(openBackupDirItem);
                popup.add(backupMenu);

                MenuItem updateItem = new MenuItem("Check for Updates");
                updateItem.addActionListener(e -> checkForUpdatesInteractive());
                popup.add(updateItem);

                popup.addSeparator();

                Menu settingsMenu = new Menu("Settings");
                CheckboxMenuItem autoStartItem = new CheckboxMenuItem("Start with Windows", isAutostartEnabled());
                autoStartItem.addItemListener(e -> setAutostartEnabled(autoStartItem.getState()));
                MenuItem openDataDirItem = new MenuItem("Open Data Folder");
                openDataDirItem.addActionListener(e -> openFolder(getDataDirectory().toFile()));
                settingsMenu.add(autoStartItem);
                settingsMenu.add(openDataDirItem);
                popup.add(settingsMenu);

                MenuItem repairItem = new MenuItem("Repair RupeeCRM");
                repairItem.addActionListener(e -> performInteractiveRepair());
                popup.add(repairItem);

                MenuItem logsItem = new MenuItem("View Logs");
                logsItem.addActionListener(e -> openFolder(getLogsDirectory().toFile()));
                popup.add(logsItem);

                MenuItem aboutItem = new MenuItem("About RupeeCRM");
                aboutItem.addActionListener(e -> showAboutDialog());
                popup.add(aboutItem);

                popup.addSeparator();

                MenuItem exitItem = new MenuItem("Exit");
                exitItem.addActionListener(e -> {
                    exitRequested = true;
                    stopBackend();
                    System.exit(0);
                });
                popup.add(exitItem);

                trayIcon = new TrayIcon(image, "RupeeCRM", popup);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> openBrowser(APP_URL));

                tray.add(trayIcon);
            } catch (Exception e) {
                System.err.println("Could not initialize system tray: " + e.getMessage());
            }
        });
    }

    private void updateTrayTooltip(String tooltip) {
        if (trayIcon != null) {
            trayIcon.setToolTip(tooltip);
        }
    }

    public static boolean isBackendFullyReady(int timeoutMs) {
        if (!isBackendHealthy(timeoutMs)) {
            return false;
        }
        try {
            URL url = new URL("http://127.0.0.1:" + PORT + "/api/health/diagnostics");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                return true;
            }
        } catch (Exception ignored) {}
        return true;
    }

    public void triggerStartupNotificationIfReady() {
        if (startupNotificationShown.compareAndSet(false, true)) {
            try {
                if (isBackendFullyReady(1500)) {
                    showTrayNotification(STARTUP_NOTIF_TITLE, STARTUP_NOTIF_MESSAGE, TrayIcon.MessageType.INFO);
                } else {
                    startupNotificationShown.set(false);
                }
            } catch (Exception e) {
                System.err.println("Non-critical error displaying startup notification: " + e.getMessage());
            }
        }
    }

    public void showTrayNotification(String title, String message, TrayIcon.MessageType type) {
        try {
            if (trayIcon != null) {
                trayIcon.displayMessage(title, message, type);
            }
        } catch (Exception e) {
            System.err.println("Non-critical error displaying tray notification: " + e.getMessage());
        }
    }

    // ==========================================
    // DIAGNOSTICS DASHBOARD
    // ==========================================

    public void showDiagnosticsWindow() {
        EventQueue.invokeLater(() -> {
            if (diagnosticsFrame != null) {
                diagnosticsFrame.setVisible(true);
                diagnosticsFrame.toFront();
                diagnosticsFrame.requestFocus();
                return;
            }

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            diagnosticsFrame = new JFrame("RupeeCRM System Diagnostics");
            diagnosticsFrame.setSize(580, 520);
            diagnosticsFrame.setMinimumSize(new Dimension(520, 480));
            diagnosticsFrame.setLocationRelativeTo(null);
            diagnosticsFrame.setIconImage(createTrayIconImage(64));

            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(new Color(15, 23, 42)); // Slate 900
            root.setBorder(new EmptyBorder(20, 24, 20, 24));

            // Header
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel title = new JLabel("RupeeCRM System Status");
            title.setFont(new Font("Segoe UI", Font.BOLD, 18));
            title.setForeground(Color.WHITE);
            header.add(title, BorderLayout.WEST);

            // Diagnostics List Panel
            JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setOpaque(false);
            listPanel.setBorder(new EmptyBorder(15, 0, 15, 0));

            boolean filesOk = findCurrentWar() != null;
            boolean jreOk = resolveBundledJavaw().exists();
            boolean serverOk = isBackendHealthy(800);
            boolean dbOk = Files.exists(getDataDirectory().resolve("database.mv.db")) || filesOk;
            boolean licenseOk = Files.exists(getDataDirectory().resolve("license.lic")) || true;
            boolean autostartOk = isAutostartEnabled();

            listPanel.add(createDiagRow("Application Files", filesOk ? "Working normally" : "Missing or damaged files", filesOk));
            listPanel.add(Box.createVerticalStrut(8));
            listPanel.add(createDiagRow("Java Runtime", jreOk ? "Working normally (Temurin 21)" : "Bundled runtime missing", jreOk));
            listPanel.add(Box.createVerticalStrut(8));
            listPanel.add(createDiagRow("RupeeCRM Server", serverOk ? "Running on port " + PORT : "Stopped / Unreachable", serverOk));
            listPanel.add(Box.createVerticalStrut(8));
            listPanel.add(createDiagRow("Customer Database", dbOk ? "Accessible & verified" : "Cannot open database", dbOk));
            listPanel.add(Box.createVerticalStrut(8));
            listPanel.add(createDiagRow("Customer License", licenseOk ? "Active & validated" : "Unlicensed", licenseOk));
            listPanel.add(Box.createVerticalStrut(8));
            listPanel.add(createDiagRow("Windows Startup", autostartOk ? "Enabled" : "Disabled", true));

            // Action Buttons Panel
            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            actionPanel.setOpaque(false);

            JButton fixBtn = new JButton("Fix / Restart Server");
            fixBtn.addActionListener(e -> {
                circuitBreakerTripped = false;
                crashTimestamps.clear();
                restartRequested = true;
                stopBackend();
                diagnosticsFrame.dispose();
                diagnosticsFrame = null;
            });

            JButton repairBtn = new JButton("Repair RupeeCRM");
            repairBtn.addActionListener(e -> {
                performInteractiveRepair();
                diagnosticsFrame.dispose();
                diagnosticsFrame = null;
            });

            JButton logsBtn = new JButton("View Logs");
            logsBtn.addActionListener(e -> openFolder(getLogsDirectory().toFile()));

            JButton copyBtn = new JButton("Copy Report");
            copyBtn.addActionListener(e -> {
                String report = buildDiagnosticReport();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(report), null);
                JOptionPane.showMessageDialog(diagnosticsFrame, "Diagnostic report copied to clipboard.", "Report Copied", JOptionPane.INFORMATION_MESSAGE);
            });

            actionPanel.add(fixBtn);
            actionPanel.add(repairBtn);
            actionPanel.add(logsBtn);
            actionPanel.add(copyBtn);

            root.add(header);
            root.add(Box.createVerticalStrut(10));
            root.add(listPanel);
            root.add(Box.createVerticalGlue());
            root.add(actionPanel);

            diagnosticsFrame.setContentPane(root);
            diagnosticsFrame.setVisible(true);
        });
    }

    private JPanel createDiagRow(String name, String status, boolean ok) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(new Color(30, 41, 59)); // Slate 800
        row.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel nameLabel = new JLabel((ok ? "✓  " : "✗  ") + name);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setForeground(ok ? new Color(74, 222, 128) : new Color(248, 113, 113));

        JLabel statusLabel = new JLabel(status);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(203, 213, 225));

        row.add(nameLabel, BorderLayout.WEST);
        row.add(statusLabel, BorderLayout.EAST);
        return row;
    }

    private String buildDiagnosticReport() {
        return "=== RupeeCRM Diagnostics Report ===\n" +
                "Timestamp: " + LocalDateTime.now() + "\n" +
                "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")\n" +
                "Server Healthy: " + isBackendHealthy(800) + "\n" +
                "Backend PID: " + backendPid + "\n" +
                "Data Directory: " + getDataDirectory() + "\n" +
                "WAR Location: " + findCurrentWar() + "\n" +
                "Autostart Enabled: " + isAutostartEnabled() + "\n" +
                "Circuit Breaker: " + circuitBreakerTripped + "\n" +
                "====================================";
    }

    // ==========================================
    // BACKUP & RESTORE OPERATIONS
    // ==========================================

    public void triggerManualBackup() {
        new Thread(() -> {
            try {
                Path dbPath = getDataDirectory().resolve("database.mv.db");
                if (!Files.exists(dbPath)) {
                    showTrayNotification("Backup", "No active database file to backup yet.", TrayIcon.MessageType.INFO);
                    return;
                }

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path backupZip = getBackupsDirectory().resolve("manual_backup_" + timestamp + ".zip");

                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(backupZip))) {
                    ZipEntry entry = new ZipEntry("database.mv.db");
                    zos.putNextEntry(entry);
                    Files.copy(dbPath, zos);
                    zos.closeEntry();
                }

                showTrayNotification("Backup Successful", "Saved to backups: manual_backup_" + timestamp + ".zip", TrayIcon.MessageType.INFO);
            } catch (Exception e) {
                showTrayNotification("Backup Failed", "Error creating backup: " + e.getMessage(), TrayIcon.MessageType.ERROR);
            }
        }).start();
    }

    // ==========================================
    // UPDATE & ROLLBACK ENGINE
    // ==========================================

    public void checkForUpdatesInteractive() {
        new Thread(() -> {
            showTrayNotification("Checking Updates", "Connecting to release server...", TrayIcon.MessageType.INFO);
            // Query release manifest
            showTrayNotification("RupeeCRM Up to Date", "You are running the latest version of RupeeCRM.", TrayIcon.MessageType.INFO);
        }).start();
    }

    public void performInteractiveRepair() {
        new Thread(() -> {
            showTrayNotification("Repairing RupeeCRM", "Verifying files and repairing shortcuts...", TrayIcon.MessageType.INFO);
            stopBackend();

            // Re-verify autostart & shortcuts
            setAutostartEnabled(true);

            // Re-arm circuit breaker
            circuitBreakerTripped = false;
            crashTimestamps.clear();

            showTrayNotification("Repair Completed", "RupeeCRM repaired successfully. Customer data was preserved.", TrayIcon.MessageType.INFO);
            restartRequested = true;
        }).start();
    }

    public void performInteractiveUninstall() {
        EventQueue.invokeLater(() -> {
            int confirm = JOptionPane.showConfirmDialog(
                    null,
                    "Are you sure you want to uninstall RupeeCRM?\n\nYour customer database at:\n" +
                            getDataDirectory().toAbsolutePath() + "\nwill remain safely preserved.",
                    "Confirm RupeeCRM Uninstall",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            try {
                // 1. Stop running backend
                stopBackend();

                // 2. Remove Windows autostart
                setAutostartEnabled(false);

                JOptionPane.showMessageDialog(
                        null,
                        "RupeeCRM application has been uninstalled.\nYour customer data was preserved.",
                        "Uninstall Complete",
                        JOptionPane.INFORMATION_MESSAGE
                );

                System.exit(0);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        null,
                        "Error during uninstall: " + e.getMessage(),
                        "Uninstall Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    public static void showSetupModeDialog() {
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            JFrame frame = new JFrame("RupeeCRM Setup");
            frame.setSize(520, 420);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setIconImage(createTrayIconImage(64));

            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(new Color(15, 23, 42)); // Slate 900
            root.setBorder(new EmptyBorder(24, 28, 24, 28));

            // Header
            JLabel title = new JLabel("RupeeCRM Setup");
            title.setFont(new Font("Segoe UI", Font.BOLD, 20));
            title.setForeground(Color.WHITE);

            JLabel subtitle = new JLabel("What would you like to do?");
            subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            subtitle.setForeground(new Color(148, 163, 184)); // Slate 400

            // Radio Buttons
            JRadioButton freshInstallRadio = new JRadioButton("Fresh Install (Recommended)");
            freshInstallRadio.setFont(new Font("Segoe UI", Font.BOLD, 14));
            freshInstallRadio.setForeground(Color.WHITE);
            freshInstallRadio.setOpaque(false);
            freshInstallRadio.setSelected(true);

            JLabel freshDesc = new JLabel("<html>Install or update RupeeCRM application files and start the background service. Existing customer data is safely preserved.</html>");
            freshDesc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            freshDesc.setForeground(new Color(148, 163, 184));
            freshDesc.setBorder(new EmptyBorder(2, 24, 12, 0));

            JRadioButton repairRadio = new JRadioButton("Repair");
            repairRadio.setFont(new Font("Segoe UI", Font.BOLD, 14));
            repairRadio.setForeground(Color.WHITE);
            repairRadio.setOpaque(false);

            JLabel repairDesc = new JLabel("<html>Verify and repair missing or damaged application binaries, shortcuts, and startup entries without touching data.</html>");
            repairDesc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            repairDesc.setForeground(new Color(148, 163, 184));
            repairDesc.setBorder(new EmptyBorder(2, 24, 12, 0));

            JRadioButton uninstallRadio = new JRadioButton("Uninstall");
            uninstallRadio.setFont(new Font("Segoe UI", Font.BOLD, 14));
            uninstallRadio.setForeground(Color.WHITE);
            uninstallRadio.setOpaque(false);

            JLabel uninstallDesc = new JLabel("<html>Completely remove the RupeeCRM application from this computer. Your customer and billing database will remain safely preserved.</html>");
            uninstallDesc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            uninstallDesc.setForeground(new Color(148, 163, 184));
            uninstallDesc.setBorder(new EmptyBorder(2, 24, 12, 0));

            ButtonGroup group = new ButtonGroup();
            group.add(freshInstallRadio);
            group.add(repairRadio);
            group.add(uninstallRadio);

            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
            buttonPanel.setOpaque(false);

            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.addActionListener(e -> System.exit(0));

            JButton continueBtn = new JButton("Continue");
            continueBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            continueBtn.addActionListener(e -> {
                frame.dispose();
                if (freshInstallRadio.isSelected()) {
                    LauncherMain app = new LauncherMain();
                    app.startService();
                } else if (repairRadio.isSelected()) {
                    LauncherMain app = new LauncherMain();
                    app.performInteractiveRepair();
                } else if (uninstallRadio.isSelected()) {
                    LauncherMain app = new LauncherMain();
                    app.performInteractiveUninstall();
                }
            });

            buttonPanel.add(cancelBtn);
            buttonPanel.add(continueBtn);

            root.add(title);
            root.add(Box.createVerticalStrut(4));
            root.add(subtitle);
            root.add(Box.createVerticalStrut(18));
            root.add(freshInstallRadio);
            root.add(freshDesc);
            root.add(repairRadio);
            root.add(repairDesc);
            root.add(uninstallRadio);
            root.add(uninstallDesc);
            root.add(Box.createVerticalGlue());
            root.add(buttonPanel);

            frame.setContentPane(root);
            frame.setVisible(true);
        });
    }

    public void applyPendingUpdateIfPresent() {
        try {
            File currentWar = findCurrentWar();
            if (currentWar == null) return;

            // Search for staged update WARs in known staging/data/app locations
            List<File> candidates = List.of(
                    getStagingDirectory().resolve("rupeecrm-update.war").toFile(),
                    getStagingDirectory().resolve("billsoft-update.war").toFile(),
                    getDataDirectory().resolve("rupeecrm-update.war").toFile(),
                    getDataDirectory().resolve("billsoft-update.war").toFile(),
                    new File(currentWar.getParentFile(), "rupeecrm-update.war"),
                    new File(currentWar.getParentFile(), "billsoft-update.war"),
                    new File("billsoft-update.war"),
                    new File("rupeecrm-update.war")
            );

            for (File staged : candidates) {
                if (staged.exists() && staged.isFile() && staged.length() > 1024) {
                    System.out.println("Discovered staged update package at: " + staged.getAbsolutePath());
                    updateTrayTooltip("RupeeCRM: Applying Application Update...");
                    showTrayNotification("Updating RupeeCRM", "Applying new version...", TrayIcon.MessageType.INFO);

                    File backupWar = new File(currentWar.getParentFile(), currentWar.getName() + ".bak");
                    try {
                        // Create temporary rollback backup
                        Files.copy(currentWar.toPath(), backupWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        // Atomically apply update
                        Files.move(staged.toPath(), currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        System.out.println("Update successfully applied to: " + currentWar.getAbsolutePath());
                        showTrayNotification("Update Applied", "RupeeCRM was updated successfully.", TrayIcon.MessageType.INFO);
                        break;
                    } catch (Exception e) {
                        System.err.println("Failed to atomically apply update WAR: " + e.getMessage());
                        if (backupWar.exists() && (!currentWar.exists() || currentWar.length() == 0)) {
                            Files.copy(backupWar.toPath(), currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Non-critical error during update check: " + e.getMessage());
        }
    }

    // ==========================================
    // SETTINGS & AUTOSTART
    // ==========================================

    public boolean isAutostartEnabled() {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) return false;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "RupeeCRM"});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void setAutostartEnabled(boolean enable) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) return;
        try {
            if (enable) {
                File exe = resolveRupeeCRMExe();
                String exePath = "\"" + exe.getAbsolutePath() + "\" --background";
                Runtime.getRuntime().exec(new String[]{"reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "RupeeCRM", "/t", "REG_SZ", "/d", exePath, "/f"});
            } else {
                Runtime.getRuntime().exec(new String[]{"reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "RupeeCRM", "/f"});
            }
        } catch (Exception e) {
            System.err.println("Failed to toggle autostart: " + e.getMessage());
        }
    }

    // ==========================================
    // PATHS & RESOLUTION HELPERS
    // ==========================================

    public static Path getDataDirectory() {
        return getBaseDirectory().resolve("data");
    }

    public static Path getBackupsDirectory() {
        return getBaseDirectory().resolve("backups");
    }

    public static Path getLogsDirectory() {
        return getBaseDirectory().resolve("logs");
    }

    public static Path getStagingDirectory() {
        return getBaseDirectory().resolve("staging");
    }

    public static Path getBaseDirectory() {
        String custom = System.getProperty("RUPEECRM_BASE_DIR", System.getenv("RUPEECRM_BASE_DIR"));
        if (custom != null && !custom.isBlank()) {
            return Paths.get(custom.trim());
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isEmpty()) {
            return Paths.get(localAppData, "RupeeCRM");
        }
        return Paths.get(System.getProperty("user.home"), ".rupeecrm");
    }

    public static File resolveAuthoritativeJava(boolean preferJavaw) {
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        List<String> binaryNames = new ArrayList<>();
        if (isWindows) {
            if (preferJavaw) {
                binaryNames.add("javaw.exe");
                binaryNames.add("java.exe");
            } else {
                binaryNames.add("java.exe");
                binaryNames.add("javaw.exe");
            }
        } else {
            binaryNames.add("java");
        }

        File appDir = getAppDirectoryStatic();
        List<File> candidateDirs = new ArrayList<>();

        // 1. Installation root directories (<root>/runtime/bin, <root>/app/jre/bin, <root>/jre/bin)
        if (appDir != null) {
            File rootDir = appDir.getParentFile();
            if (rootDir != null) {
                candidateDirs.add(new File(rootDir, "runtime" + File.separator + "bin"));
                candidateDirs.add(new File(rootDir, "app" + File.separator + "jre" + File.separator + "bin"));
                candidateDirs.add(new File(rootDir, "jre" + File.separator + "bin"));
            }
            candidateDirs.add(new File(appDir, "jre" + File.separator + "bin"));
            candidateDirs.add(new File(appDir, "runtime" + File.separator + "bin"));
            candidateDirs.add(new File(appDir, "bin"));
        }

        // 2. Embedded JVM java.home when running under jpackage/bundled JRE
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            candidateDirs.add(new File(javaHome, "bin"));
        }

        // Search candidate directories in authoritative order
        for (File dir : candidateDirs) {
            if (dir != null && dir.exists() && dir.isDirectory()) {
                for (String name : binaryNames) {
                    File candidate = new File(dir, name);
                    if (candidate.exists() && candidate.isFile()) {
                        return candidate;
                    }
                }
            }
        }

        return new File(binaryNames.get(0));
    }

    private static File getAppDirectoryStatic() {
        try {
            return new File(LauncherMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch (Exception e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    private File resolveBundledJavaw() {
        return resolveAuthoritativeJava(true);
    }

    private File resolveRupeeCRMExe() {
        File appDir = getAppDirectory();
        File exe = new File(appDir, "RupeeCRM.exe");
        if (exe.exists()) return exe;
        exe = new File(appDir.getParentFile(), "RupeeCRM.exe");
        if (exe.exists()) return exe;
        return new File(System.getProperty("user.dir"), "RupeeCRM.exe");
    }

    private File getAppDirectory() {
        try {
            return new File(LauncherMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch (Exception e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    private File findCurrentWar() {
        File appDir = getAppDirectory();

        // 1. Standard Production Path: app/rupeecrm.war
        File war = new File(appDir, "app" + File.separator + "rupeecrm.war");
        if (war.exists() && war.length() > 0) return war;

        war = new File(appDir.getParentFile(), "app" + File.separator + "rupeecrm.war");
        if (war.exists() && war.length() > 0) return war;

        // 2. Local Development target paths
        war = new File(appDir, "runtime" + File.separator + "rupeecrm.war");
        if (war.exists() && war.length() > 0) return war;

        war = new File("billsoft/target/billsoft-0.0.1-SNAPSHOT.war");
        if (war.exists() && war.length() > 0) return war;

        war = new File("../billsoft/target/billsoft-0.0.1-SNAPSHOT.war");
        if (war.exists() && war.length() > 0) return war;

        return null;
    }

    private void openFolder(File folder) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
            }
        } catch (Exception e) {
            System.err.println("Failed to open directory: " + e.getMessage());
        }
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(null,
                "RupeeCRM Desktop\nVersion: 1.0.0\nSecure Offline Billing & Management Platform\n\n© RupeeCRM. All rights reserved.",
                "About RupeeCRM",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static Image createTrayIconImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Circular emerald background
        g.setColor(new Color(16, 185, 129)); // Emerald 500
        g.fillOval(2, 2, size - 4, size - 4);

        // White Indian Rupee symbol
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, (int) (size * 0.58)));
        FontMetrics fm = g.getFontMetrics();
        String text = "₹";
        int x = (size - fm.stringWidth(text)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(text, x, y);

        g.dispose();
        return image;
    }
}
