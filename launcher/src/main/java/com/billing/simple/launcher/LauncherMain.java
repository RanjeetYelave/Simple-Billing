package com.billing.simple.launcher;

import java.awt.*;
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

public class LauncherMain {

    private static final String APP_URL = "http://app.rupeecrm.localhost:8080/";
    private static final String HEALTH_URL = "http://localhost:8080/api/health";
    private static final int PORT = 8080;

    private Process backendProcess;
    private TrayIcon trayIcon;
    private boolean isBackgroundMode = false;
    private volatile boolean restartRequested = false;
    private volatile boolean exitRequested = false;

    public static void main(String[] args) {
        // Ensure AWT GUI subsystem is active for SystemTray
        System.setProperty("java.awt.headless", "false");

        boolean background = false;
        for (String arg : args) {
            if ("--background".equalsIgnoreCase(arg) || "-b".equalsIgnoreCase(arg)) {
                background = true;
            }
        }

        // 1. Single-instance check: if server is already running, focus/open browser and exit
        if (isBackendHealthy(1000)) {
            System.out.println("RupeeCRM service is already running. Opening browser...");
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
        System.out.println("Starting RupeeCRM Background Service...");

        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopBackend));

        // Auto-register Windows Startup on first run (Registry + Startup Folder VBS)
        setupWindowsAutoStart(true);

        // Setup System Tray on GUI Event Thread
        setupSystemTray();

        // Run the main service management loop
        runServiceLoop();
    }

    private void runServiceLoop() {
        while (true) {
            File warFile = findCurrentWar();
            if (warFile == null || !warFile.exists()) {
                showTrayNotification("Error", "RupeeCRM WAR package not found.", TrayIcon.MessageType.ERROR);
                System.err.println("Fatal: RupeeCRM WAR file not found.");
                break;
            }

            try {
                System.out.println("Launching backend: " + warFile.getAbsolutePath());
                backendProcess = launchBackendProcess(warFile);
            } catch (IOException e) {
                System.err.println("Failed to launch backend: " + e.getMessage());
                showTrayNotification("Startup Failed", "Could not start backend process: " + e.getMessage(), TrayIcon.MessageType.ERROR);
                break;
            }

            // Wait for backend to be healthy (up to 30s)
            boolean healthy = waitForBackend(30);
            if (!healthy) {
                System.err.println("Backend failed to become healthy. Initiating rollback...");
                handleBootFailure(backendProcess);
                continue;
            }

            System.out.println("RupeeCRM backend is healthy and running on port " + PORT);
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
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
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

        // Lightweight JVM tuning for client background service
        command.add("-Xms128m");
        command.add("-Xmx512m");
        command.add("-XX:+TieredCompilation");
        command.add("-XX:TieredStopAtLevel=1");
        command.add("-jar");
        command.add(warFile.getAbsolutePath());
        command.add("--server.port=" + PORT);
        command.add("--server.address=0.0.0.0");

        ProcessBuilder pb = new ProcessBuilder(command);
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
                Image image = createTrayIconImage();

                PopupMenu popup = new PopupMenu();

                MenuItem openItem = new MenuItem("🌐 Open RupeeCRM (Browser)");
                openItem.addActionListener(e -> openBrowser(APP_URL));
                popup.add(openItem);

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

                CheckboxMenuItem autoStartItem = new CheckboxMenuItem("🚀 Start on Windows Boot", isWindowsAutoStartEnabled());
                autoStartItem.addItemListener(e -> setupWindowsAutoStart(autoStartItem.getState()));
                popup.add(autoStartItem);

                MenuItem restartItem = new MenuItem("🔄 Restart Service");
                restartItem.addActionListener(e -> restartService());
                popup.add(restartItem);

                popup.addSeparator();

                MenuItem exitItem = new MenuItem("🛑 Exit RupeeCRM");
                exitItem.addActionListener(e -> exitApplication());
                popup.add(exitItem);

                trayIcon = new TrayIcon(image, "RupeeCRM (Active)", popup);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> openBrowser(APP_URL)); // Click opens browser

                tray.add(trayIcon);
                System.out.println("System tray icon initialized successfully for RupeeCRM.");
            } catch (Exception e) {
                System.err.println("Failed to initialize system tray: " + e.getMessage());
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

    private Image createTrayIconImage() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Vibrant gradient circle from Indigo (#4f46e5) to Royal Violet (#7c3aed)
        GradientPaint gp = new GradientPaint(0, 0, new Color(99, 102, 241), size, size, new Color(124, 58, 237));
        g2.setPaint(gp);
        g2.fillOval(2, 2, size - 4, size - 4);

        // Soft subtle border
        g2.setColor(new Color(255, 255, 255, 70));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawOval(2, 2, size - 4, size - 4);

        // Draw crisp Indian Rupee symbol (₹)
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Upper bar 1: x: 10 -> 22, y: 9
        g2.drawLine(10, 9, 22, 9);
        // Upper bar 2: x: 10 -> 20, y: 13
        g2.drawLine(10, 13, 20, 13);
        // Spine & Top loop
        g2.drawLine(14, 9, 14, 18);
        g2.drawArc(8, 9, 12, 9, -90, 180);
        // Diagonal slash
        g2.drawLine(14, 18, 22, 24);

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
        System.out.println("User requested service restart from system tray.");
        showTrayNotification("Restarting", "Restarting RupeeCRM service...", TrayIcon.MessageType.INFO);
        restartRequested = true;
        stopBackend();
    }

    private void exitApplication() {
        System.out.println("User requested exit from system tray.");
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
        killProcessOnPort(PORT);
    }

    private void killProcessOnPort(int port) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Process p = Runtime.getRuntime().exec(new String[]{
                    "cmd.exe", "/c",
                    "for /f \"tokens=5\" %a in ('netstat -aon ^| findstr :" + port + " ^| findstr LISTENING') do taskkill /f /pid %a"
                });
                p.waitFor(3, TimeUnit.SECONDS);
            } else {
                Process p = Runtime.getRuntime().exec(new String[]{
                    "/bin/sh", "-c", "lsof -ti :" + port + " | xargs kill -9 2>/dev/null || true"
                });
                p.waitFor(3, TimeUnit.SECONDS);
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
        try {
            Files.copy(currentWar.toPath(), backupWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(updateWar.toPath(), currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(updateWar.toPath());
            return true;
        } catch (IOException e) {
            System.err.println("Failed to apply update: " + e.getMessage());
            return false;
        }
    }

    private void handleBootFailure(Process failedProcess) {
        if (failedProcess != null && failedProcess.isAlive()) {
            failedProcess.destroy();
            try {
                failedProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }

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
    }

    private Path getDataDirectory() {
        String envPath = System.getenv("RUPEECRM_DATA_DIR");
        if (envPath != null && !envPath.trim().isEmpty()) {
            return Paths.get(envPath.trim());
        }
        envPath = System.getenv("BILLSOFT_DATA_DIR");
        if (envPath != null && !envPath.trim().isEmpty()) {
            return Paths.get(envPath.trim());
        }
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                return Paths.get(appData, "SimpleBilling");
            }
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "SimpleBilling");
        }
        return Paths.get(System.getProperty("user.home"), ".simplebilling");
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
}
