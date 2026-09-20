package com.billing.simple.launcher;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Native Desktop Supervisor & Control Center for RupeeCRM.
 * Manages background billing engine, system tray integration, auto-start, and browser launching.
 */
public class LauncherMain {

    public static final String APP_URL = "http://management.rupeecrm.local:28080/";
    public static final String HEALTH_URL = "http://127.0.0.1:28080/api/health";
    public static final int PORT = 28080;

    private Process backendProcess;
    private TrayIcon trayIcon;
    private JFrame controlFrame;
    private boolean isBackgroundMode = false;
    private volatile boolean restartRequested = false;
    private static final AtomicBoolean rollbackRequested = new AtomicBoolean(false);
    private volatile boolean exitRequested = false;

    // UI state elements
    private JLabel statusBadge;
    private JLabel statusDetailLabel;
    private JProgressBar progressBar;
    private JButton openBrowserBtn;
    private JButton minimizeTrayBtn;
    private JButton reattachTrayBtn;
    private JCheckBox autoStartCheckbox;
    private JLabel countdownLabel;
    private JButton stayOpenBtn;
    private javax.swing.Timer autoMinimizeTimer;
    private int countdownSeconds = 8;

    public static void main(String[] args) {
        // Ensure AWT GUI subsystem is active for SystemTray & Swing
        System.setProperty("java.awt.headless", "false");

        boolean background = false;
        for (String arg : args) {
            if ("--background".equalsIgnoreCase(arg) || "-b".equalsIgnoreCase(arg)) {
                background = true;
            }
        }

        // 1. Single-instance check: if server is already running, focus/open browser and notify
        if (isBackendHealthy(1200)) {
            System.out.println("RupeeCRM service is already active. Opening browser at " + APP_URL);
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

        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopBackend));

        // Auto-configure local hostname mapping if writable
        setupLocalHostname();

        // Auto-register Windows Startup on first run (Registry + Startup Folder VBS) on Windows only
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            setupWindowsAutoStart(true);
        }

        // Setup System Tray
        setupSystemTray();

        // Create & show Desktop Control Center window if not background mode
        if (!isBackgroundMode) {
            showControlCenter();
        }

        // Run the main service management loop in background worker
        new Thread(this::runServiceLoop, "RupeeCRM-Supervisor-Thread").start();
    }

    private void showControlCenter() {
        EventQueue.invokeLater(() -> {
            try {
                if (controlFrame != null) {
                    controlFrame.setVisible(true);
                    controlFrame.toFront();
                    controlFrame.requestFocus();
                    return;
                }

                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}

                controlFrame = new JFrame("RupeeCRM - Service Manager & Assistant");
                controlFrame.setSize(560, 520);
                controlFrame.setMinimumSize(new Dimension(500, 480));
                controlFrame.setLocationRelativeTo(null);
                controlFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                controlFrame.setIconImage(createTrayIconImage(64));

                controlFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        // Minimizing to tray on close
                        minimizeToTray();
                    }
                });

                JPanel mainPanel = new JPanel();
                mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
                mainPanel.setBackground(new Color(15, 23, 42)); // Slate 900
                mainPanel.setBorder(new EmptyBorder(20, 24, 20, 24));

                // 1. HEADER CARD
                JPanel headerCard = createCardPanel(new BorderLayout(14, 10));
                
                JPanel titleRow = new JPanel(new BorderLayout(12, 0));
                titleRow.setOpaque(false);

                JLabel logoLabel = new JLabel(new ImageIcon(createTrayIconImage(48)));
                
                JPanel textCol = new JPanel();
                textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
                textCol.setOpaque(false);

                JLabel appTitle = new JLabel("RupeeCRM Billing & Management");
                appTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
                appTitle.setForeground(Color.WHITE);

                JLabel appSubtitle = new JLabel("Offline Desktop Background Service");
                appSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                appSubtitle.setForeground(new Color(148, 163, 184)); // Slate 400

                textCol.add(appTitle);
                textCol.add(Box.createVerticalStrut(3));
                textCol.add(appSubtitle);

                titleRow.add(logoLabel, BorderLayout.WEST);
                titleRow.add(textCol, BorderLayout.CENTER);

                // Status Badge
                statusBadge = new JLabel("  🟡 Initializing Service...  ");
                statusBadge.setOpaque(true);
                statusBadge.setBackground(new Color(51, 65, 85)); // Slate 700
                statusBadge.setForeground(new Color(251, 191, 36)); // Amber 400
                statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
                statusBadge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(100, 116, 139), 1),
                        new EmptyBorder(4, 8, 4, 8)
                ));

                JPanel badgeWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                badgeWrapper.setOpaque(false);
                badgeWrapper.add(statusBadge);
                titleRow.add(badgeWrapper, BorderLayout.EAST);

                headerCard.add(titleRow, BorderLayout.NORTH);

                // Progress Bar & Step details
                JPanel progressBox = new JPanel();
                progressBox.setLayout(new BoxLayout(progressBox, BoxLayout.Y_AXIS));
                progressBox.setOpaque(false);
                progressBox.setBorder(new EmptyBorder(12, 0, 4, 0));

                progressBar = new JProgressBar();
                progressBar.setIndeterminate(true);
                progressBar.setPreferredSize(new Dimension(480, 8));
                progressBar.setMaximumSize(new Dimension(Short.MAX_VALUE, 8));
                progressBar.setForeground(new Color(99, 102, 241)); // Indigo 500
                progressBar.setBackground(new Color(30, 41, 59)); // Slate 800
                progressBar.setBorder(BorderFactory.createEmptyBorder());

                statusDetailLabel = new JLabel("Starting local database & embedded server...");
                statusDetailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                statusDetailLabel.setForeground(new Color(203, 213, 225)); // Slate 300
                statusDetailLabel.setBorder(new EmptyBorder(6, 0, 0, 0));

                progressBox.add(progressBar);
                progressBox.add(statusDetailLabel);
                headerCard.add(progressBox, BorderLayout.SOUTH);

                mainPanel.add(headerCard);
                mainPanel.add(Box.createVerticalStrut(14));

                // 2. PRIMARY ACTION CONTROLS
                JPanel actionsCard = createCardPanel(new BorderLayout(10, 10));

                JLabel actionsTitle = new JLabel("Quick Actions & Access");
                actionsTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
                actionsTitle.setForeground(new Color(226, 232, 240));
                actionsCard.add(actionsTitle, BorderLayout.NORTH);

                JPanel buttonsGrid = new JPanel(new GridLayout(2, 2, 10, 10));
                buttonsGrid.setOpaque(false);

                openBrowserBtn = createStyledButton("🌐 Open in Browser (Port 28080)", new Color(79, 70, 229), Color.WHITE);
                openBrowserBtn.addActionListener(e -> openBrowser(APP_URL));

                minimizeTrayBtn = createStyledButton("📌 Minimize to System Tray", new Color(30, 41, 59), new Color(226, 232, 240));
                minimizeTrayBtn.addActionListener(e -> minimizeToTray());

                reattachTrayBtn = createStyledButton("➕ Add / Re-attach to Tray", new Color(30, 41, 59), new Color(226, 232, 240));
                reattachTrayBtn.addActionListener(e -> {
                    refreshTrayIcon();
                    showTrayNotification("System Tray", "Tray icon refreshed and active in taskbar notification area.", TrayIcon.MessageType.INFO);
                    JOptionPane.showMessageDialog(controlFrame, 
                            "System Tray icon has been refreshed.\nLook for the ₹ icon near your Windows clock.",
                            "System Tray Active", JOptionPane.INFORMATION_MESSAGE);
                });

                JButton openDataBtn = createStyledButton("📂 Open Data Folder", new Color(30, 41, 59), new Color(226, 232, 240));
                openDataBtn.addActionListener(e -> {
                    try {
                        Desktop.getDesktop().open(getDataDirectory().toFile());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(controlFrame, "Data Directory: " + getDataDirectory().toAbsolutePath(), "Data Folder", JOptionPane.INFORMATION_MESSAGE);
                    }
                });

                buttonsGrid.add(openBrowserBtn);
                buttonsGrid.add(minimizeTrayBtn);
                buttonsGrid.add(reattachTrayBtn);
                buttonsGrid.add(openDataBtn);

                actionsCard.add(buttonsGrid, BorderLayout.CENTER);
                mainPanel.add(actionsCard);
                mainPanel.add(Box.createVerticalStrut(14));

                // 3. SETTINGS & AUTO-START CARD
                JPanel settingsCard = createCardPanel(new BorderLayout(10, 8));

                JLabel settingsTitle = new JLabel("Background Preferences");
                settingsTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
                settingsTitle.setForeground(new Color(226, 232, 240));
                settingsCard.add(settingsTitle, BorderLayout.NORTH);

                JPanel settingsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
                settingsRow.setOpaque(false);

                boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
                if (isWin) {
                    autoStartCheckbox = new JCheckBox("🚀 Automatically start RupeeCRM service on Windows boot (Silent background mode)");
                    autoStartCheckbox.setSelected(isWindowsAutoStartEnabled());
                    autoStartCheckbox.setOpaque(false);
                    autoStartCheckbox.setForeground(new Color(203, 213, 225));
                    autoStartCheckbox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    autoStartCheckbox.addActionListener(e -> {
                        boolean enable = autoStartCheckbox.isSelected();
                        setupWindowsAutoStart(enable);
                    });
                    settingsRow.add(autoStartCheckbox);
                }

                JLabel trayInfo = new JLabel("💡 Tip: RupeeCRM runs quietly in the background. Close this window anytime to minimize.");
                trayInfo.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                trayInfo.setForeground(new Color(148, 163, 184));
                settingsRow.add(trayInfo);

                settingsCard.add(settingsRow, BorderLayout.CENTER);
                mainPanel.add(settingsCard);
                mainPanel.add(Box.createVerticalStrut(14));

                // 4. FOOTER & COUNTDOWN BAR
                JPanel footerBar = new JPanel(new BorderLayout(8, 0));
                footerBar.setOpaque(false);

                JPanel countdownPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                countdownPanel.setOpaque(false);

                countdownLabel = new JLabel("");
                countdownLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                countdownLabel.setForeground(new Color(148, 163, 184));

                stayOpenBtn = new JButton("Stay Open");
                stayOpenBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                stayOpenBtn.setForeground(Color.WHITE);
                stayOpenBtn.setBackground(new Color(51, 65, 85));
                stayOpenBtn.setFocusPainted(false);
                stayOpenBtn.setVisible(false);
                stayOpenBtn.addActionListener(e -> cancelAutoMinimize());

                countdownPanel.add(countdownLabel);
                countdownPanel.add(stayOpenBtn);

                JPanel rightFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
                rightFooter.setOpaque(false);

                JButton restartBtn = new JButton("🔄 Restart Service");
                restartBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                restartBtn.setForeground(new Color(203, 213, 225));
                restartBtn.setBackground(new Color(30, 41, 59));
                restartBtn.addActionListener(e -> restartService());

                JButton exitBtn = new JButton("🛑 Stop & Exit");
                exitBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                exitBtn.setForeground(new Color(248, 113, 113));
                exitBtn.setBackground(new Color(30, 41, 59));
                exitBtn.addActionListener(e -> exitApplication());

                rightFooter.add(restartBtn);
                rightFooter.add(exitBtn);

                footerBar.add(countdownPanel, BorderLayout.WEST);
                footerBar.add(rightFooter, BorderLayout.EAST);

                mainPanel.add(footerBar);

                controlFrame.setContentPane(mainPanel);
                controlFrame.setVisible(true);
            } catch (Exception e) {
                System.err.println("Could not display control center window: " + e.getMessage());
            }
        });
    }

    private JPanel createCardPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(30, 41, 59)); // Slate 800
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(51, 65, 85)); // Slate 700 border
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));
        return panel;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(bg.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(bg.brighter());
                } else {
                    g2.setColor(bg);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(fg);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(200, 38));
        return btn;
    }

    private void updateStatusToHealthy() {
        EventQueue.invokeLater(() -> {
            if (statusBadge != null) {
                statusBadge.setText("  🟢 Active & Healthy (Port 28080)  ");
                statusBadge.setBackground(new Color(6, 78, 59)); // Emerald 900
                statusBadge.setForeground(new Color(52, 211, 153)); // Emerald 400
                statusBadge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(16, 185, 129), 1),
                        new EmptyBorder(4, 8, 4, 8)
                ));
            }
            if (statusDetailLabel != null) {
                statusDetailLabel.setText("RupeeCRM billing engine is live at " + APP_URL);
            }
            if (progressBar != null) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
                progressBar.setForeground(new Color(16, 185, 129));
            }

            // Start auto-minimize countdown if window is open and not running headless
            if (!isBackgroundMode && controlFrame != null && controlFrame.isVisible()) {
                startAutoMinimizeCountdown();
            }
        });
    }

    private void updateStatusToError(String message) {
        EventQueue.invokeLater(() -> {
            if (statusBadge != null) {
                statusBadge.setText("  🔴 Service Failed  ");
                statusBadge.setBackground(new Color(127, 29, 29)); // Red 900
                statusBadge.setForeground(new Color(248, 113, 113)); // Red 400
            }
            if (statusDetailLabel != null) {
                statusDetailLabel.setText(message);
            }
            if (progressBar != null) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
            }
        });
    }

    private void startAutoMinimizeCountdown() {
        countdownSeconds = 10;
        if (stayOpenBtn != null) stayOpenBtn.setVisible(true);

        if (autoMinimizeTimer != null && autoMinimizeTimer.isRunning()) {
            autoMinimizeTimer.stop();
        }

        autoMinimizeTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                countdownSeconds--;
                if (countdownSeconds > 0) {
                    if (countdownLabel != null) {
                        countdownLabel.setText("Minimizing to system tray in " + countdownSeconds + "s...");
                    }
                } else {
                    cancelAutoMinimize();
                    minimizeToTray();
                }
            }
        });
        autoMinimizeTimer.start();
        if (countdownLabel != null) {
            countdownLabel.setText("Minimizing to system tray in " + countdownSeconds + "s...");
        }
    }

    private void cancelAutoMinimize() {
        if (autoMinimizeTimer != null) {
            autoMinimizeTimer.stop();
            autoMinimizeTimer = null;
        }
        if (countdownLabel != null) {
            countdownLabel.setText("RupeeCRM is active in background.");
        }
        if (stayOpenBtn != null) {
            stayOpenBtn.setVisible(false);
        }
    }

    public void minimizeToTray() {
        EventQueue.invokeLater(() -> {
            cancelAutoMinimize();
            if (controlFrame != null) {
                controlFrame.setVisible(false);
            }
            showTrayNotification("RupeeCRM Active", "RupeeCRM is running in your system tray.\nClick the ₹ icon anytime to open.", TrayIcon.MessageType.INFO);
        });
    }

    private void runServiceLoop() {
        while (true) {
            // Detect external rollback request marker
            try {
                Path marker = getDataDirectory().resolve("rollback.request");
                if (Files.exists(marker)) {
                    rollbackRequested.set(true);
                }
            } catch (Exception ignored) {}

            File warFile = findCurrentWar();
            if (warFile == null || !warFile.exists()) {
                updateStatusToError("RupeeCRM WAR package not found.");
                showTrayNotification("Error", "RupeeCRM WAR package not found.", TrayIcon.MessageType.ERROR);
                System.err.println("Fatal: RupeeCRM WAR file not found.");
                break;
            }

            try {
                System.out.println("Launching backend: " + warFile.getAbsolutePath());
                backendProcess = launchBackendProcess(warFile);
            } catch (IOException e) {
                updateStatusToError("Failed to launch backend: " + e.getMessage());
                System.err.println("Failed to launch backend: " + e.getMessage());
                showTrayNotification("Startup Failed", "Could not start backend process: " + e.getMessage(), TrayIcon.MessageType.ERROR);
                break;
            }

            // Wait for backend to be healthy (up to 90s)
            boolean healthy = waitForBackend(90);
            if (!healthy) {
                updateStatusToError("Backend startup timed out. Initiating rollback...");
                System.err.println("Backend failed to become healthy within 90s. Initiating rollback...");
                handleBootFailure(backendProcess);
                continue;
            }

            System.out.println("RupeeCRM backend is healthy and running on port " + PORT);
            updateStatusToHealthy();
            showTrayNotification("RupeeCRM Ready", "Service is running at " + APP_URL, TrayIcon.MessageType.INFO);

            // If launched interactively by user double-click, open browser
            if (!isBackgroundMode) {
                openBrowser(APP_URL);
            }

            // Monitor backend process execution
            try {
                int exitCode = backendProcess.waitFor();
                System.out.println("Backend process stopped with exit code: " + exitCode);
            } catch (InterruptedException e) {
                System.err.println("Launcher service interrupted: " + e.getMessage());
                break;
            }

            if (exitRequested) {
                System.out.println("Exit requested. Terminating supervisor loop.");
                break;
            }

            if (restartRequested) {
                restartRequested = false;
                System.out.println("Manual service restart requested. Re-launching backend...");
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                continue;
            }

            // Check for manual rollback request
            if (rollbackRequested.get()) {
                rollbackRequested.set(false);
                System.out.println("Manual rollback requested. Performing rollback and restarting...");
                try {
                    performRollback();
                } catch (Exception e) {
                    System.err.println("Rollback failed: " + e.getMessage());
                }
                continue;
            }

            // Check if there is an update pending
            File updateWar = findUpdateWar();
            if (updateWar != null && updateWar.exists() && updateWar.length() > 0) {
                System.out.println("Pending update found. Applying update...");
                showTrayNotification("Updating", "Applying RupeeCRM update...", TrayIcon.MessageType.INFO);
                boolean prepared = prepareAndApplyUpdate(updateWar);
                if (prepared) {
                    System.out.println("Update applied successfully. Restarting service...");
                    continue;
                }
            }

            // Backend stopped unexpectedly without user exit request
            System.out.println("Backend process stopped. Re-launching in 2 seconds...");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
        }

        System.out.println("RupeeCRM service shutting down.");
        System.exit(0);
    }

    private Process launchBackendProcess(File warFile) throws IOException {
        List<String> command = new ArrayList<>();

        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + (System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");

        File javaBinFile = new File(javaBin);
        if (javaBinFile.exists()) {
            command.add(javaBinFile.getAbsolutePath());
        } else {
            command.add("java");
        }

        // JVM tuning for client background service
        command.add("-Xms128m");
        command.add("-Xmx512m");
        command.add("-XX:+TieredCompilation");
        command.add("-XX:TieredStopAtLevel=1");
        command.add("-jar");
        command.add(warFile.getAbsolutePath());
        command.add("--server.port=" + PORT);
        command.add("--server.address=127.0.0.1");

        ProcessBuilder pb = new ProcessBuilder(command);
        if (warFile.getParentFile() != null && warFile.getParentFile().exists()) {
            pb.directory(warFile.getParentFile());
        }
        try {
            Path logsDir = getDataDirectory().resolve("logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }
            pb.redirectOutput(ProcessBuilder.Redirect.to(logsDir.resolve("backend-stdout.log").toFile()));
            pb.redirectError(ProcessBuilder.Redirect.to(logsDir.resolve("backend-stderr.log").toFile()));
        } catch (Exception e) {
            pb.redirectErrorStream(true);
        }
        return pb.start();
    }

    private static boolean isBackendHealthy(int timeoutMs) {
        try {
            URL url = new URL(HEALTH_URL);
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

    private void setupSystemTray() {
        EventQueue.invokeLater(() -> {
            try {
                if (!SystemTray.isSupported()) {
                    System.out.println("System tray is not supported on this platform environment.");
                    return;
                }

                SystemTray tray = SystemTray.getSystemTray();
                Image image = createTrayIconImage(32);

                PopupMenu popup = new PopupMenu();

                MenuItem openItem = new MenuItem("🌐 Open RupeeCRM (Browser)");
                openItem.addActionListener(e -> openBrowser(APP_URL));
                popup.add(openItem);

                MenuItem managerItem = new MenuItem("🖥️ Show Control Center");
                managerItem.addActionListener(e -> showControlCenter());
                popup.add(managerItem);

                popup.addSeparator();

                MenuItem dataDirItem = new MenuItem("📂 Open Data Folder");
                dataDirItem.addActionListener(e -> {
                    try {
                        Desktop.getDesktop().open(getDataDirectory().toFile());
                    } catch (Exception ex) {
                        System.err.println("Cannot open data directory: " + ex.getMessage());
                    }
                });
                popup.add(dataDirItem);

                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    CheckboxMenuItem autoStartItem = new CheckboxMenuItem("🚀 Start on Windows Boot", isWindowsAutoStartEnabled());
                    autoStartItem.addItemListener(e -> setupWindowsAutoStart(autoStartItem.getState()));
                    popup.add(autoStartItem);
                }

                MenuItem restartItem = new MenuItem("🔄 Restart Service");
                restartItem.addActionListener(e -> restartService());
                popup.add(restartItem);

                popup.addSeparator();

                MenuItem exitItem = new MenuItem("🛑 Exit RupeeCRM");
                exitItem.addActionListener(e -> exitApplication());
                popup.add(exitItem);

                trayIcon = new TrayIcon(image, "RupeeCRM (Active)", popup);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> openBrowser(APP_URL)); // Single click opens browser

                tray.add(trayIcon);
                System.out.println("System tray icon initialized successfully for RupeeCRM.");
            } catch (Exception e) {
                System.err.println("Failed to initialize system tray: " + e.getMessage());
            }
        });
    }

    public void refreshTrayIcon() {
        EventQueue.invokeLater(() -> {
            try {
                if (!SystemTray.isSupported()) return;
                SystemTray tray = SystemTray.getSystemTray();
                if (trayIcon != null) {
                    try {
                        tray.remove(trayIcon);
                    } catch (Exception ignored) {}
                }
                setupSystemTray();
            } catch (Exception e) {
                System.err.println("Failed to refresh system tray: " + e.getMessage());
            }
        });
    }

    private void showTrayNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            EventQueue.invokeLater(() -> {
                try {
                    trayIcon.displayMessage(title, message, type);
                } catch (Exception ignored) {}
            });
        }
    }

    private Image createTrayIconImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Vibrant gradient circle from Indigo (#4f46e5) to Royal Violet (#7c3aed)
        GradientPaint gp = new GradientPaint(0, 0, new Color(99, 102, 241), size, size, new Color(124, 58, 237));
        g2.setPaint(gp);
        int pad = Math.max(1, size / 16);
        g2.fillOval(pad, pad, size - (pad * 2), size - (pad * 2));

        // Soft subtle border
        g2.setColor(new Color(255, 255, 255, 70));
        g2.setStroke(new BasicStroke(Math.max(1.0f, size / 24.0f)));
        g2.drawOval(pad, pad, size - (pad * 2), size - (pad * 2));

        // Scale factors for Indian Rupee symbol (₹)
        float s = size / 32.0f;
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2.2f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Upper bar 1: x: 10 -> 22, y: 9
        g2.drawLine(Math.round(10 * s), Math.round(9 * s), Math.round(22 * s), Math.round(9 * s));
        // Upper bar 2: x: 10 -> 20, y: 13
        g2.drawLine(Math.round(10 * s), Math.round(13 * s), Math.round(20 * s), Math.round(13 * s));
        // Spine & Top loop
        g2.drawLine(Math.round(14 * s), Math.round(9 * s), Math.round(14 * s), Math.round(18 * s));
        g2.drawArc(Math.round(8 * s), Math.round(9 * s), Math.round(12 * s), Math.round(9 * s), -90, 180);
        // Diagonal slash
        g2.drawLine(Math.round(14 * s), Math.round(18 * s), Math.round(22 * s), Math.round(24 * s));

        g2.dispose();
        return image;
    }

    private void setupWindowsAutoStart(boolean enable) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) return;

        try {
            File currentExe = getAppExecutable();
            if (currentExe == null || !currentExe.exists()) return;

            String exePath = currentExe.getAbsolutePath();

            // 1. Windows Registry Auto-Start: HKCU\Software\Microsoft\Windows\CurrentVersion\Run
            String keyPath = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
            String appName = "RupeeCRMService";

            if (enable) {
                String cmd = String.format("reg add \"%s\" /v \"%s\" /t REG_SZ /d \"\\\"%s\\\" --background\" /f",
                        keyPath, appName, exePath);
                Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", cmd});
            } else {
                String cmd = String.format("reg delete \"%s\" /v \"%s\" /f", keyPath, appName);
                Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", cmd});
            }

            // 2. Windows User Startup Folder VBS Fallback (100% Reliable across all Windows versions)
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                File startupDir = new File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup");
                if (startupDir.exists()) {
                    File vbsFile = new File(startupDir, "RupeeCRM.vbs");
                    if (enable) {
                        try (FileWriter writer = new FileWriter(vbsFile)) {
                            writer.write("Set WshShell = CreateObject(\"WScript.Shell\")\r\n");
                            writer.write("WshShell.Run \"\"\"" + exePath.replace("\\", "\\\\") + "\"\" --background\", 0, False\r\n");
                        }
                    } else {
                        vbsFile.delete();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to update Windows startup registration: " + e.getMessage());
        }
    }

    private boolean isWindowsAutoStartEnabled() {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) return false;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "cmd.exe", "/c", "reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v \"RupeeCRMService\""
            });
            boolean regOk = (p.waitFor() == 0);
            if (regOk) return true;

            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                File vbsFile = new File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup\\RupeeCRM.vbs");
                return vbsFile.exists();
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private File getAppExecutable() {
        try {
            File codeSourceFile = new File(LauncherMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (codeSourceFile.getName().toLowerCase().endsWith(".exe")) {
                return codeSourceFile;
            }
            File parentDir = codeSourceFile.getParentFile();
            if (parentDir != null) {
                File exe = new File(parentDir, "RupeeCRM.exe");
                if (exe.exists()) return exe;
                File oldExe = new File(parentDir, "Billsoft.exe");
                if (oldExe.exists()) return oldExe;

                File grandParent = parentDir.getParentFile();
                if (grandParent != null) {
                    File grandExe = new File(grandParent, "RupeeCRM.exe");
                    if (grandExe.exists()) return grandExe;
                    File oldGrandExe = new File(grandParent, "Billsoft.exe");
                    if (oldGrandExe.exists()) return oldGrandExe;
                }
            }

            File userDirExe = new File(System.getProperty("user.dir"), "RupeeCRM.exe");
            if (userDirExe.exists()) return userDirExe;

            return codeSourceFile;
        } catch (Exception e) {
            return null;
        }
    }

    private void restartService() {
        System.out.println("User requested service restart.");
        showTrayNotification("Restarting", "Restarting RupeeCRM service...", TrayIcon.MessageType.INFO);
        restartRequested = true;
        stopBackend();
        refreshTrayIcon();
    }

    private void exitApplication() {
        System.out.println("User requested exit.");
        exitRequested = true;
        stopBackend();
        System.exit(0);
    }

    private void stopBackend() {
        if (backendProcess != null && backendProcess.isAlive()) {
            try {
                backendProcess.destroy();
                if (!backendProcess.waitFor(3, TimeUnit.SECONDS)) {
                    backendProcess.destroyForcibly();
                    backendProcess.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ignored) {
                if (backendProcess != null) {
                    backendProcess.destroyForcibly();
                }
            }
        }
    }

    public static void setupLocalHostname() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Path hostsPath = os.contains("win")
                    ? Paths.get(System.getenv("SystemRoot") != null ? System.getenv("SystemRoot") : "C:\\Windows", "System32", "drivers", "etc", "hosts")
                    : Paths.get("/etc/hosts");

            if (Files.exists(hostsPath) && Files.isWritable(hostsPath)) {
                String content = Files.readString(hostsPath);
                if (!content.contains("management.rupeecrm.local")) {
                    String entry = (content.endsWith("\n") ? "" : "\n") + "127.0.0.1 management.rupeecrm.local\n";
                    Files.writeString(hostsPath, entry, java.nio.file.StandardOpenOption.APPEND);
                    System.out.println("Configured management.rupeecrm.local in hosts file.");
                }
            }
        } catch (Exception ignored) {}
    }

    private File getAppDirectory() {
        try {
            File codeSourceFile = new File(LauncherMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return codeSourceFile.getParentFile();
        } catch (Exception e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    private File resolveFile(String relativePath) {
        File appDir = getAppDirectory();
        File file = new File(appDir, relativePath);
        if (file.exists()) return file;

        file = new File(appDir.getParentFile(), relativePath);
        if (file.exists()) return file;

        if (appDir.getParentFile() != null) {
            file = new File(appDir.getParentFile().getParentFile(), relativePath);
            if (file.exists()) return file;
        }

        return new File(relativePath);
    }

    private File findCurrentWar() {
        File current = resolveFile("runtime/rupeecrm.war");
        if (current.exists()) return current;

        current = resolveFile("runtime/billsoft.war");
        if (current.exists()) return current;

        current = resolveFile("billsoft/target/billsoft-0.0.1-SNAPSHOT.war");
        if (current.exists()) return current;

        current = resolveFile("../billsoft/target/billsoft-0.0.1-SNAPSHOT.war");
        if (current.exists()) return current;

        return null;
    }

    private File findUpdateWar() {
        File update = resolveFile("runtime/rupeecrm-update.war");
        if (update.exists() && update.length() > 0) return update;

        update = resolveFile("runtime/billsoft-update.war");
        if (update.exists() && update.length() > 0) return update;

        update = resolveFile("rupeecrm-update.war");
        if (update.exists() && update.length() > 0) return update;

        update = resolveFile("billsoft-update.war");
        if (update.exists() && update.length() > 0) return update;

        Path dataDir = getDataDirectory();
        if (dataDir != null) {
            File dataUpdate = dataDir.resolve("billsoft-update.war").toFile();
            if (dataUpdate.exists() && dataUpdate.length() > 0) return dataUpdate;

            dataUpdate = dataDir.resolve("rupeecrm-update.war").toFile();
            if (dataUpdate.exists() && dataUpdate.length() > 0) return dataUpdate;
        }

        update = resolveFile("billsoft/target/billsoft-update.war");
        if (update.exists() && update.length() > 0) return update;

        update = resolveFile("../billsoft/target/billsoft-update.war");
        if (update.exists() && update.length() > 0) return update;

        return null;
    }

    private boolean prepareAndApplyUpdate(File updateWar) {
        File currentWar = findCurrentWar();
        if (currentWar == null || !currentWar.exists()) {
            return false;
        }

        Path dataDir = getDataDirectory();
        backupDatabase(dataDir);

        File backupWar = new File(currentWar.getParentFile(), "rupeecrm-backup.war");
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                if (currentWar.getParentFile() != null && !currentWar.getParentFile().exists()) {
                    currentWar.getParentFile().mkdirs();
                }
                Files.copy(currentWar.toPath(), backupWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(updateWar.toPath(), currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(updateWar.toPath());
                System.out.println("Update applied successfully on attempt " + attempt);
                return true;
            } catch (IOException e) {
                System.err.println("Attempt " + attempt + " to apply update failed: " + e.getMessage() + ". Retrying in 1s...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }
        System.err.println("Failed to apply update after 5 attempts.");
        return false;
    }

    private void handleBootFailure(Process failedProcess) {
        if (failedProcess != null && failedProcess.isAlive()) {
            failedProcess.destroy();
            try {
                failedProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }
        performRollback();
    }

    public void performRollback() {
        File currentWar = findCurrentWar();
        if (currentWar == null) return;

        File backupWar = new File(currentWar.getParentFile(), "rupeecrm-backup.war");
        Path dataDir = getDataDirectory();

        rollbackDatabase(dataDir);

        if (backupWar.exists()) {
            try {
                Files.copy(backupWar.toPath(), currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(backupWar.toPath());
            } catch (IOException e) {
                System.err.println("Failed to rollback WAR: " + e.getMessage());
            }
        }
        // Cleanup rollback marker file if present
        try {
            Path marker = dataDir.resolve("rollback.request");
            Files.deleteIfExists(marker);
        } catch (IOException ignored) {}
    }

    private Path getDataDirectory() {
        return getDataDirectoryStatic();
    }

    private void backupDatabase(Path dataDir) {
        Path dbFile = dataDir.resolve("database.mv.db");
        if (!Files.exists(dbFile)) return;

        Path backupDir = dataDir.resolve("backup");
        try {
            Files.createDirectories(backupDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupFile = backupDir.resolve("database_" + timestamp + ".mv.db");
            Files.copy(dbFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            Path latestBackup = dataDir.resolve("database_latest_backup.mv.db");
            Files.copy(dbFile, latestBackup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to backup database: " + e.getMessage());
        }
    }

    private void rollbackDatabase(Path dataDir) {
        Path latestBackup = dataDir.resolve("database_latest_backup.mv.db");
        Path dbFile = dataDir.resolve("database.mv.db");

        if (Files.exists(latestBackup)) {
            try {
                Files.copy(latestBackup, dbFile, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(latestBackup);
            } catch (IOException e) {
                System.err.println("Failed to rollback database: " + e.getMessage());
            }
        }
    }

    public static void requestRollback() {
        try {
            Path marker = getDataDirectoryStatic().resolve("rollback.request");
            Files.createFile(marker);
            rollbackRequested.set(true);
        } catch (IOException e) {
            System.err.println("Failed to create rollback request file: " + e.getMessage());
        }
    }

    private static Path getDataDirectoryStatic() {
        String custom = System.getProperty("RUPEECRM_DATA_DIR");
        if (custom == null || custom.trim().isEmpty()) {
            custom = System.getenv("RUPEECRM_DATA_DIR");
        }
        if (custom == null || custom.trim().isEmpty()) {
            custom = System.getProperty("BILLSOFT_DATA_DIR");
        }
        if (custom == null || custom.trim().isEmpty()) {
            custom = System.getenv("BILLSOFT_DATA_DIR");
        }

        if (custom != null && !custom.trim().isEmpty()) {
            Path customPath = Paths.get(custom.trim());
            try {
                if (!Files.exists(customPath)) {
                    Files.createDirectories(customPath);
                }
            } catch (Exception ignored) {}
            return customPath;
        }

        String os = System.getProperty("os.name").toLowerCase();
        Path targetDir;
        Path legacyDir = null;

        if (os.contains("mac")) {
            targetDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "RupeeCRM");
            legacyDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "SimpleBilling");
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                targetDir = Paths.get(appData, "SimpleBilling");
            } else {
                targetDir = Paths.get(System.getProperty("user.home"), ".simplebilling");
            }
        } else {
            targetDir = Paths.get(System.getProperty("user.home"), ".rupeecrm");
            legacyDir = Paths.get(System.getProperty("user.home"), ".simplebilling");
        }

        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // Safe One-Time Migration: Only if target database doesn't exist and legacy database DOES exist
            if (legacyDir != null && Files.exists(legacyDir)) {
                Path targetDb = targetDir.resolve("database.mv.db");
                Path legacyDb = legacyDir.resolve("database.mv.db");
                if (!Files.exists(targetDb) && Files.exists(legacyDb)) {
                    System.out.println("Detected legacy database. Performing safe one-time migration from " + legacyDir + " to " + targetDir);
                    Path finalLegacy = legacyDir;
                    Path finalTarget = targetDir;
                    Files.walkFileTree(finalLegacy, new java.nio.file.SimpleFileVisitor<Path>() {
                        @Override
                        public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                            Path rel = finalLegacy.relativize(dir);
                            Path dest = finalTarget.resolve(rel.toString());
                            if (!Files.exists(dest)) {
                                Files.createDirectories(dest);
                            }
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }

                        @Override
                        public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                            Path rel = finalLegacy.relativize(file);
                            Path dest = finalTarget.resolve(rel.toString());
                            if (!Files.exists(dest)) {
                                Files.copy(file, dest, StandardCopyOption.COPY_ATTRIBUTES);
                            }
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Data directory initialization note: " + e.getMessage());
        }

        return targetDir;
    }
}
