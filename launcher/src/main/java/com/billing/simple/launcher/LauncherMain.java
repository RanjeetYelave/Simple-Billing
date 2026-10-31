package com.billing.simple.launcher;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
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
import com.billing.simple.launcher.update.UpdateCrypto;
import com.billing.simple.launcher.update.UpdateManifest;
import com.billing.simple.launcher.update.UpdateState;

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

    public static final int PORT = resolvePort();
    public static final String APP_URL = "http://localhost:" + PORT + "/";
    public static final String HEALTH_URL = "http://127.0.0.1:" + PORT + "/api/health";

    public static int resolvePort() {
        String p = System.getProperty("rupeecrm.port");
        if (p == null || p.trim().isEmpty()) {
            p = System.getenv("RUPEECRM_PORT");
        }
        if (p != null && !p.trim().isEmpty()) {
            try {
                return Integer.parseInt(p.trim());
            } catch (Exception ignored) {}
        }
        return 28080;
    }

    public static final String STARTUP_NOTIF_TITLE = "RupeeCRM";
    public static final String STARTUP_NOTIF_MESSAGE = "App started successfully\nStatus: Healthy\nReady to use";

    // Embedded Master Ed25519 Public Key for Release Manifest Authentication
    public static final String DEFAULT_PUBLIC_KEY_X509_BASE64 =
            "MCowBQYDK2VwAyEAtdG8eJgrX0L7jtx13wOGnqSeeID2B/aRv1+OZQ7v3C8=";

    private Process backendProcess;
    private Long backendPid = null;
    private TrayIcon trayIcon;
    private JFrame diagnosticsFrame;
    private boolean isBackgroundMode = false;
    private volatile boolean restartRequested = false;
    private volatile boolean exitRequested = false;
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);

    // Candidate update tracking
    private volatile boolean isCandidateLaunch = false;
    private volatile String candidateVersion = null;
    private volatile File activeBackupWar = null;

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

    // Edge Application Mode process tracking
    static volatile Process edgeAppProcess = null;
    static volatile long lastWindowLaunchTime = 0;

    // Single-instance supervisor lock
    static FileChannel supervisorLockChannel = null;
    static FileLock supervisorLock = null;

    // Supervisor IPC Activation Server for single-instance secondary launches
    static ServerSocket activationServerSocket = null;
    static String activationToken = null;
    static Thread activationServerThread = null;

    public static void main(String[] args) {
        if (System.getProperty("java.awt.headless") == null) {
            boolean hasDisplay = System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null || System.getProperty("os.name").toLowerCase().contains("win");
            System.setProperty("java.awt.headless", hasDisplay ? "false" : "true");
        }

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

        // Single-instance detection & supervisor lock:
        Path handoffToken = getStagingDirectory().resolve("update.handoff");
        Path handoffTokenTx = getBaseDirectory().resolve("staging").resolve("update-tx").resolve("update.handoff");
        boolean updateHandoff = Files.exists(handoffToken) || Files.exists(handoffTokenTx);

        boolean healthy = isBackendHealthy(1200);

        if (healthy && !updateHandoff) {
            System.out.println("RupeeCRM backend is already active.");
            if (!background) {
                if (!notifyActiveSupervisorToActivate()) {
                    openApplicationWindow(APP_URL);
                }
            }
            System.exit(0);
            return;
        }

        // Try to acquire the supervisor file lock to ensure strictly one supervisor owns the tray and backend
        if (!acquireSupervisorLock()) {
            System.out.println("Another RupeeCRM supervisor instance is already running.");
            if (!background) {
                if (!notifyActiveSupervisorToActivate()) {
                    if (healthy) {
                        openApplicationWindow(APP_URL);
                    } else {
                        System.out.println("Waiting for RupeeCRM backend to become ready...");
                        if (waitForBackendHealthy(15)) {
                            openApplicationWindow(APP_URL);
                        }
                    }
                }
            }
            System.exit(0);
            return;
        }

        startActivationServer();

        LauncherMain app = new LauncherMain();
        app.isBackgroundMode = background;
        app.startService();
    }

    public void startService() {
        System.out.println("Starting RupeeCRM Supervisor & Background Service...");

        // Ensure persistent directories exist
        ensureDirectories();

        // Start local loopback activation server for secondary launcher invocations
        startActivationServer();

        // Ensure autostart registration is initialized on Windows
        if (System.getProperty("os.name").toLowerCase().contains("win") && !isAutostartEnabled()) {
            setAutostartEnabled(true);
        }

        // Setup shutdown hook to cleanly terminate Edge app window, RupeeCRM backend, and supervisor lock
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopEdgeApp();
            stopBackend();
            releaseSupervisorLock();
        }));

        // Setup System Tray
        setupSystemTray();

        // If not in background mode, open application window once healthy
        if (!isBackgroundMode) {
            new Thread(() -> {
                if (waitForBackend(25)) {
                    openApplicationWindow(APP_URL);
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
            Files.createDirectories(getBrowserProfileDirectory());
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

            recoverInterruptedTransactionIfPresent();
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

                    if (isCandidateLaunch) {
                        boolean verified = verifyRunningCandidateVersion(candidateVersion);
                        if (verified) {
                            Path stateFile = getStagingDirectory().resolve("update-state.json");
                            UpdateState state = UpdateState.load(stateFile);
                            state.setStatus(UpdateState.STATUS_COMMITTED);
                            try { state.saveAtomic(stateFile); } catch (Exception ignored) {}

                            if (activeBackupWar != null && activeBackupWar.exists()) {
                                activeBackupWar.delete();
                            }
                            isCandidateLaunch = false;
                            showTrayNotification("Update Succeeded", "RupeeCRM updated successfully to v" + candidateVersion, TrayIcon.MessageType.INFO);
                        } else {
                            System.err.println("CRITICAL: Candidate version mismatch! Expected: " + candidateVersion + ". Initiating rollback...");
                            healthy = false;
                        }
                    }
                }
                if (!healthy) {
                    System.err.println("RupeeCRM backend failed health or version verification.");
                    if (isCandidateLaunch && activeBackupWar != null && activeBackupWar.exists()) {
                        System.err.println("CRITICAL: Candidate version v" + candidateVersion + " failed startup health probe. Initiating supervisor rollback to " + activeBackupWar.getName());
                        updateTrayTooltip("RupeeCRM: Rolling back to previous version...");
                        showTrayNotification("Update Failed - Rolling Back", "The new version failed to start. Restoring previous version...", TrayIcon.MessageType.WARNING);

                        stopBackend();

                        Path stateFile = getStagingDirectory().resolve("update-state.json");
                        UpdateState state = UpdateState.load(stateFile);
                        state.setStatus(UpdateState.STATUS_ROLLBACK_IN_PROGRESS);
                        try { state.saveAtomic(stateFile); } catch (Exception ignored) {}

                        try {
                            Files.copy(activeBackupWar.toPath(), warFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("Rollback restored binary: " + warFile.getAbsolutePath());
                            state.setStatus(UpdateState.STATUS_ROLLED_BACK);
                            state.setErrorMessage("Version v" + candidateVersion + " failed health check; rolled back to previous binary");
                            state.saveAtomic(stateFile);
                            showTrayNotification("Previous Version Restored", "RupeeCRM restored your previous working version. Customer data was safely preserved.", TrayIcon.MessageType.INFO);
                        } catch (Exception e) {
                            System.err.println("CRITICAL: Failed to restore backup binary: " + e.getMessage());
                            state.setStatus(UpdateState.STATUS_RECOVERY_REQUIRED);
                            state.setErrorMessage("Rollback restore failed: " + e.getMessage());
                            try { state.saveAtomic(stateFile); } catch (Exception ignored) {}
                        }

                        isCandidateLaunch = false;
                        restartRequested = true;
                        continue;
                    } else {
                        recordCrashEvent();
                    }
                }

                // Wait for process termination
                int exitCode = backendProcess.waitFor();
                System.out.println("Backend process exited with code: " + exitCode);
                backendPid = null;

                if (exitCode == 10) {
                    restartRequested = true;
                    System.out.println("Backend requested graceful restart via Exit Code 10.");
                }

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
        if (!javaBin.exists()) {
            System.err.println("CRITICAL: Bundled Java runtime executable not found at: " + javaBin.getAbsolutePath());
            throw new FileNotFoundException("Bundled Java runtime not found at: " + javaBin.getAbsolutePath());
        }
        System.out.println("Using authoritative bundled Java runtime: " + javaBin.getAbsolutePath());
        command.add(javaBin.getAbsolutePath());

        // JVM memory and execution parameters
        command.add("-Xms128m");
        command.add("-Xmx512m");
        command.add("-XX:+TieredCompilation");
        command.add("-XX:TieredStopAtLevel=1");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Djava.awt.headless=true");
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

    private boolean verifyRunningCandidateVersion(String candidateVersion) {
        if (candidateVersion == null || candidateVersion.trim().isEmpty()) {
            return true;
        }
        try {
            URL url = new URL("http://127.0.0.1:" + PORT + "/api/system/update-status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (body.contains("\"currentVersion\":\"" + candidateVersion + "\"") ||
                    body.contains("\"status\":\"SUCCESS\"") ||
                    body.contains("\"status\":\"HEALTH_VERIFIED\"") ||
                    body.contains("\"status\":\"COMMITTED\"")) {
                    return true;
                }
                if (body.contains("\"currentVersion\":") && !body.contains(candidateVersion)) {
                    System.err.println("Running backend reported version does not match candidate " + candidateVersion + ": " + body);
                    return false;
                }
            }
        } catch (Exception e) {
            System.err.println("Note: unable to query /api/system/update-status for version verification: " + e.getMessage());
        }
        try {
            Path v1 = getDataDirectory().resolve(".billsoft-version");
            if (Files.exists(v1)) {
                String v = Files.readString(v1, StandardCharsets.UTF_8).trim();
                if (candidateVersion.equals(v)) return true;
            }
        } catch (Exception ignored) {}
        return true;
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

    public static boolean waitForBackendHealthy(int maxSeconds) {
        long start = System.currentTimeMillis();
        long maxMs = maxSeconds * 1000L;
        while (System.currentTimeMillis() - start < maxMs) {
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

    public static synchronized Process getEdgeAppProcess() {
        return edgeAppProcess;
    }

    public static synchronized void setEdgeAppProcess(Process p) {
        edgeAppProcess = p;
    }

    /**
     * Centralized method responsible for opening the RupeeCRM desktop application window.
     * On Windows, launches Microsoft Edge in standalone App Mode with a dedicated profile.
     * On other platforms (or when Edge is absent), delegates to the system default browser.
     */
    public static synchronized boolean openApplicationWindow(String urlStr) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            long now = System.currentTimeMillis();
            if (now - lastWindowLaunchTime < 1000) {
                System.out.println("Ignoring rapid duplicate application window launch request.");
                return true;
            }
            lastWindowLaunchTime = now;

            File edgeExe = resolveEdgeExecutable();
            if (edgeExe != null && edgeExe.exists()) {
                try {
                    Path profileDir = getBrowserProfileDirectory();
                    Files.createDirectories(profileDir);

                    List<String> command = buildEdgeAppArguments(edgeExe.getAbsolutePath(), urlStr, profileDir);
                    ProcessBuilder pb = new ProcessBuilder(command);
                    Path logsDir = getLogsDirectory();
                    if (Files.exists(logsDir)) {
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logsDir.resolve("edge-stdout.log").toFile()));
                        pb.redirectError(ProcessBuilder.Redirect.appendTo(logsDir.resolve("edge-stderr.log").toFile()));
                    }

                    System.out.println("Launching RupeeCRM standalone window via Microsoft Edge App Mode: " + edgeExe.getAbsolutePath());
                    Process launched = pb.start();
                    // Process handoff protection: If we already hold a live browser main process,
                    // the newly launched process is a transient handoff client that signals the existing
                    // window to focus and then terminates. Do NOT overwrite the live main process handle!
                    if (edgeAppProcess == null || !edgeAppProcess.isAlive()) {
                        edgeAppProcess = launched;
                    }
                    return true;
                } catch (Exception e) {
                    System.err.println("Error launching Microsoft Edge App Mode: " + e.getMessage());
                    showEdgeLaunchErrorDialog("Microsoft Edge Launch Error",
                            "Failed to launch Microsoft Edge in Application Mode: " + e.getMessage() + "\n\n" +
                            "RupeeCRM will open in your default system browser instead.");
                }
            } else {
                System.err.println("Microsoft Edge executable not found for application mode on Windows.");
                showEdgeLaunchErrorDialog("Microsoft Edge Not Found",
                        "Microsoft Edge was not detected on this system.\n\n" +
                        "RupeeCRM runs as a standalone desktop window using Microsoft Edge App Mode.\n" +
                        "RupeeCRM will open in your default system browser instead.\n\n" +
                        "For the dedicated desktop window experience, please install Microsoft Edge.");
            }

            // Fallback for Windows when Edge is unavailable (e.g. specialized Windows Server / LTSC):
            // Fall back to system default browser and inform the user so billing operations are never blocked.
            System.out.println("Falling back to default system browser for RupeeCRM UI.");
            openDefaultBrowser(urlStr);
            return false;
        } else {
            openDefaultBrowser(urlStr);
            return true;
        }
    }

    public static void showEdgeLaunchErrorDialog(String title, String message) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(title + ": " + message);
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            JOptionPane.showMessageDialog(
                    null,
                    message,
                    title,
                    JOptionPane.WARNING_MESSAGE
            );
        });
    }

    public static void openBrowser(String urlStr) {
        openApplicationWindow(urlStr);
    }

    public static void openDefaultBrowser(String urlStr) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(urlStr));
            } else {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", urlStr});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", urlStr});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", urlStr});
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to open default browser: " + e.getMessage());
        }
    }

    public static synchronized void stopEdgeApp() {
        if (edgeAppProcess != null) {
            System.out.println("Stopping RupeeCRM Edge application window process...");
            try {
                if (edgeAppProcess.isAlive()) {
                    // 1. Gracefully request window/process termination
                    edgeAppProcess.destroy();
                    // 2. Wait up to 2 seconds for clean exit
                    if (!edgeAppProcess.waitFor(2, TimeUnit.SECONDS)) {
                        // 3. Forcibly terminate tracked descendant processes, then root
                        try {
                            edgeAppProcess.descendants().forEach(ph -> {
                                try { ph.destroyForcibly(); } catch (Exception ignored) {}
                            });
                        } catch (Exception ignored) {}
                        edgeAppProcess.destroyForcibly();
                    }
                }
            } catch (Exception ignored) {}
            edgeAppProcess = null;
        }
    }

    public static File resolveEdgeExecutable() {
        String override = System.getProperty("rupeecrm.edge.path");
        if (override == null || override.isBlank()) {
            override = System.getenv("RUPEECRM_EDGE_PATH");
        }
        if (override != null && !override.isBlank()) {
            File f = new File(override.trim());
            if (f.exists() && f.isFile()) {
                return f;
            }
        }

        List<String> candidatePaths = new ArrayList<>();
        String progFilesX86 = System.getenv("ProgramFiles(x86)");
        if (progFilesX86 != null && !progFilesX86.isBlank()) {
            candidatePaths.add(progFilesX86 + "\\Microsoft\\Edge\\Application\\msedge.exe");
        }
        String progFiles = System.getenv("ProgramFiles");
        if (progFiles != null && !progFiles.isBlank()) {
            candidatePaths.add(progFiles + "\\Microsoft\\Edge\\Application\\msedge.exe");
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidatePaths.add(localAppData + "\\Microsoft\\Edge\\Application\\msedge.exe");
        }

        candidatePaths.add("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
        candidatePaths.add("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe");

        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }

        File regEdge = queryRegistryForEdge();
        if (regEdge != null && regEdge.exists()) {
            return regEdge;
        }

        File pathEdge = queryPathForEdge();
        if (pathEdge != null && pathEdge.exists()) {
            return pathEdge;
        }

        return null;
    }

    public static File queryRegistryForEdge() {
        String[] regCommands = {
            "reg query \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\msedge.exe\" /ve",
            "reg query \"HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\msedge.exe\" /ve"
        };
        for (String cmd : regCommands) {
            try {
                Process p = Runtime.getRuntime().exec(cmd);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("REG_SZ")) {
                            String[] parts = line.split("REG_SZ", 2);
                            if (parts.length > 1) {
                                String resolved = parts[1].trim();
                                if (!resolved.isBlank()) {
                                    File f = new File(resolved);
                                    if (f.exists() && f.isFile()) {
                                        return f;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static File queryPathForEdge() {
        try {
            Process p = Runtime.getRuntime().exec("where msedge.exe");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    File f = new File(line.trim());
                    if (f.exists() && f.isFile()) {
                        return f;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static List<String> buildEdgeAppArguments(String edgeExecutablePath, String appUrl, Path profileDir) {
        List<String> args = new ArrayList<>();
        args.add(edgeExecutablePath);
        args.add("--app=" + appUrl);
        args.add("--user-data-dir=" + profileDir.toAbsolutePath().toString());
        args.add("--no-first-run");
        args.add("--no-default-browser-check");
        args.add("--disable-features=Translate");
        args.add("--disable-background-mode");
        return args;
    }

    public static synchronized boolean acquireSupervisorLock() {
        if (supervisorLock != null && supervisorLock.isValid()) {
            return true;
        }
        try {
            Path lockFile = getBaseDirectory().resolve(".supervisor.lock");
            if (lockFile.getParent() != null) {
                Files.createDirectories(lockFile.getParent());
            }
            supervisorLockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            supervisorLock = supervisorLockChannel.tryLock();
            if (supervisorLock == null) {
                try { supervisorLockChannel.close(); } catch (Exception ignored) {}
                supervisorLockChannel = null;
                return false;
            }
            return true;
        } catch (Exception e) {
            if (supervisorLockChannel != null) {
                try { supervisorLockChannel.close(); } catch (Exception ignored) {}
                supervisorLockChannel = null;
            }
            supervisorLock = null;
            System.err.println("Note: Could not acquire supervisor lock: " + e.getMessage());
            return false;
        }
    }

    public static synchronized void releaseSupervisorLock() {
        stopActivationServer();
        try {
            if (supervisorLock != null && supervisorLock.isValid()) {
                supervisorLock.release();
            }
        } catch (Exception ignored) {}
        try {
            if (supervisorLockChannel != null && supervisorLockChannel.isOpen()) {
                supervisorLockChannel.close();
            }
        } catch (Exception ignored) {}
        supervisorLock = null;
        supervisorLockChannel = null;
    }

    // ==========================================
    // SUPERVISOR IPC ACTIVATION SERVER
    // ==========================================

    public static synchronized void startActivationServer() {
        if (activationServerSocket != null && !activationServerSocket.isClosed()) {
            return;
        }
        try {
            activationServerSocket = new ServerSocket();
            activationServerSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 10);
            int port = activationServerSocket.getLocalPort();
            activationToken = UUID.randomUUID().toString();

            Path ipcFile = getBaseDirectory().resolve(".supervisor.ipc");
            if (ipcFile.getParent() != null) {
                Files.createDirectories(ipcFile.getParent());
            }
            String content = "port=" + port + "\ntoken=" + activationToken + "\npid=" + ProcessHandle.current().pid() + "\n";
            Files.writeString(ipcFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Set<java.nio.file.attribute.PosixFilePermission> perms = EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                );
                Files.setPosixFilePermissions(ipcFile, perms);
            } catch (UnsupportedOperationException | IOException ignored) {}

            activationServerThread = new Thread(() -> {
                while (activationServerSocket != null && !activationServerSocket.isClosed()) {
                    try {
                        Socket socket = activationServerSocket.accept();
                        handleActivationConnection(socket);
                    } catch (IOException e) {
                        // Socket closed when supervisor stops
                        break;
                    }
                }
            }, "RupeeCRM-Activation-Server");
            activationServerThread.setDaemon(true);
            activationServerThread.start();
            System.out.println("Supervisor activation server listening on 127.0.0.1:" + port);
        } catch (Exception e) {
            System.err.println("Warning: Failed to start supervisor activation server: " + e.getMessage());
        }
    }

    private static void handleActivationConnection(Socket socket) {
        try (socket) {
            socket.setSoTimeout(2000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line != null && activationToken != null) {
                String expected = activationToken + " ACTIVATE";
                if (expected.equals(line.trim())) {
                    writer.write("OK\n");
                    writer.flush();
                    System.out.println("Received activation request from secondary launcher invocation. Reopening/focusing application window.");
                    new Thread(() -> {
                        if (waitForBackendHealthy(15)) {
                            openApplicationWindow(APP_URL);
                        }
                    }, "RupeeCRM-Activate-Window").start();
                    return;
                }
            }
            writer.write("DENIED\n");
            writer.flush();
        } catch (Exception ignored) {}
    }

    public static synchronized void stopActivationServer() {
        try {
            if (activationServerSocket != null && !activationServerSocket.isClosed()) {
                activationServerSocket.close();
            }
        } catch (Exception ignored) {}
        activationServerSocket = null;
        activationToken = null;
        activationServerThread = null;
        try {
            Path ipcFile = getBaseDirectory().resolve(".supervisor.ipc");
            Files.deleteIfExists(ipcFile);
        } catch (Exception ignored) {}
    }

    public static boolean notifyActiveSupervisorToActivate() {
        Path ipcFile = getBaseDirectory().resolve(".supervisor.ipc");
        if (!Files.exists(ipcFile)) {
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(ipcFile, StandardCharsets.UTF_8);
            int port = -1;
            String token = null;
            for (String line : lines) {
                if (line.startsWith("port=")) {
                    port = Integer.parseInt(line.substring("port=".length()).trim());
                } else if (line.startsWith("token=")) {
                    token = line.substring("token=".length()).trim();
                }
            }
            if (port <= 0 || token == null || token.isBlank()) {
                return false;
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1500);
                socket.setSoTimeout(2500);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                writer.write(token + " ACTIVATE\n");
                writer.flush();

                String response = reader.readLine();
                if ("OK".equalsIgnoreCase(response != null ? response.trim() : "")) {
                    System.out.println("Successfully notified running RupeeCRM supervisor to activate application window.");
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Could not notify running supervisor: " + e.getMessage());
        }
        return false;
    }

    // ==========================================
    // SYSTEM TRAY & CONTEXT MENU
    // ==========================================

    private void setupSystemTray() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                System.out.println("Headless environment detected; skipping system tray initialization.");
                return;
            }
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
                    openItem.addActionListener(e -> openApplicationWindow(APP_URL));
                    openItem.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    popup.add(openItem);

                    MenuItem restartItem = new MenuItem("Restart RupeeCRM");
                    restartItem.addActionListener(e -> {
                        restartRequested = true;
                        circuitBreakerTripped = false;
                        crashTimestamps.clear();
                        stopEdgeApp();
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
                        stopEdgeApp();
                        stopBackend();
                        releaseSupervisorLock();
                        System.exit(0);
                    });
                    popup.add(exitItem);

                    trayIcon = new TrayIcon(image, "RupeeCRM", popup);
                    trayIcon.setImageAutoSize(true);
                    trayIcon.addActionListener(e -> openApplicationWindow(APP_URL));

                    tray.add(trayIcon);
                } catch (Throwable t) {
                    System.err.println("Could not initialize system tray: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            System.err.println("System tray initialization check failed: " + t.getMessage());
        }
    }

    private void updateTrayTooltip(String tooltip) {
        if (trayIcon != null) {
            try {
                trayIcon.setToolTip(tooltip);
            } catch (Throwable ignored) {}
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
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Notification [" + type + "] " + title + ": " + message);
            return;
        }
        try {
            if (trayIcon != null) {
                trayIcon.displayMessage(title, message, type);
            }
        } catch (Throwable e) {
            System.err.println("Non-critical error displaying tray notification: " + e.getMessage());
        }
    }

    // ==========================================
    // DIAGNOSTICS DASHBOARD
    // ==========================================

    public void showDiagnosticsWindow() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Headless environment; diagnostics window not displayed.");
            return;
        }
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
            try {
                URL url = new URL("http://127.0.0.1:" + PORT + "/api/system/update-status?force=true");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    if (json.contains("\"updateAvailable\":true")) {
                        showTrayNotification("Update Available", "A newer version of RupeeCRM is available. Open RupeeCRM to install.", TrayIcon.MessageType.INFO);
                        return;
                    } else {
                        showTrayNotification("RupeeCRM Up to Date", "You are running the latest version of RupeeCRM.", TrayIcon.MessageType.INFO);
                        return;
                    }
                }
            } catch (Exception ignored) {}
            showTrayNotification("Update Check Unavailable", "Could not check for updates. The application service may be starting or offline.", TrayIcon.MessageType.WARNING);
        }, "tray-update-check-thread").start();
    }

    public void performInteractiveRepair() {
        new Thread(() -> {
            showTrayNotification("Repairing RupeeCRM", "Verifying files and repairing shortcuts...", TrayIcon.MessageType.INFO);
            stopEdgeApp();
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
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Interactive uninstall cannot be displayed in headless mode.");
            return;
        }
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
                // 1. Stop running UI and backend
                stopEdgeApp();
                stopBackend();
                releaseSupervisorLock();

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
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Setup dialog cannot be displayed in headless mode.");
            return;
        }
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

    public void recoverInterruptedTransactionIfPresent() {
        try {
            Path stateFile = getStagingDirectory().resolve("update-state.json");
            if (!Files.exists(stateFile)) {
                return;
            }

            UpdateState state = UpdateState.load(stateFile);
            String status = state.getStatus();
            if (status == null || UpdateState.STATUS_IDLE.equals(status) || UpdateState.STATUS_COMMITTED.equals(status)) {
                return;
            }

            File currentWar = findCurrentWar();
            String backupPathStr = state.getBackupArtifactPath();
            File backupWar = (backupPathStr != null && !backupPathStr.isBlank()) ? new File(backupPathStr) : null;
            if (backupWar == null && currentWar != null) {
                backupWar = new File(currentWar.getParentFile(), currentWar.getName() + ".bak");
            }

            if (UpdateState.STATUS_STARTING_NEW_VERSION.equals(status)) {
                if (backupWar != null && backupWar.exists() && backupWar.length() > 0) {
                    this.isCandidateLaunch = true;
                    this.candidateVersion = state.getTargetVersion();
                    this.activeBackupWar = backupWar;
                    System.out.println("RECOVERY: Resumed candidate update monitoring for v" + candidateVersion + " following supervisor restart.");
                } else if (currentWar != null && currentWar.exists()) {
                    state.setStatus(UpdateState.STATUS_COMMITTED);
                    try { state.saveAtomic(stateFile); } catch (Exception ignored) {}
                    System.out.println("RECOVERY: Candidate update marked committed (no backup pending).");
                }
            } else if (UpdateState.STATUS_ACTIVATION_STARTED.equals(status) || UpdateState.STATUS_ROLLBACK_IN_PROGRESS.equals(status)) {
                if (backupWar != null && backupWar.exists() && backupWar.length() > 0 && currentWar != null) {
                    System.err.println("RECOVERY: Crash detected during update activation/rollback. Restoring backup binary from " + backupWar.getAbsolutePath());
                    Files.copy(backupWar.toPath(), currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    state.setStatus(UpdateState.STATUS_ROLLED_BACK);
                    state.setErrorMessage("Interrupted update transaction recovered; restored previous working binary");
                    try { state.saveAtomic(stateFile); } catch (Exception ignored) {}
                    System.out.println("RECOVERY: Successfully restored backup binary: " + currentWar.getAbsolutePath());
                } else {
                    System.err.println("CRITICAL RECOVERY ERROR: Interrupted update activation/rollback detected, but backup binary is missing or corrupted!");
                    state.setStatus(UpdateState.STATUS_RECOVERY_REQUIRED);
                    state.setErrorMessage("Interrupted update recovery failed: backup binary missing or corrupted (" + (backupWar != null ? backupWar.getAbsolutePath() : "null") + ")");
                    try { state.saveAtomic(stateFile); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("RECOVERY WARNING: Exception while checking for interrupted update transaction: " + e.getMessage());
        }
    }

    private boolean isTestModeAllowed() {
        return System.getProperty("RUPEECRM_BASE_DIR") != null ||
               Boolean.getBoolean("app.test.mode") ||
               System.getProperty("app.update.test-override-key") != null;
    }

    public boolean isCandidateLaunch() { return isCandidateLaunch; }
    public String getCandidateVersion() { return candidateVersion; }
    public File getActiveBackupWar() { return activeBackupWar; }

    public boolean applyPendingUpdateIfPresent() {
        try {
            Path handoffToken = getStagingDirectory().resolve("update.handoff");
            if (!Files.exists(handoffToken)) {
                // No handoff requested, do not modify active binary
                return false;
            }

            Path stateFile = getStagingDirectory().resolve("update-state.json");
            UpdateState state = UpdateState.load(stateFile);

            Path txDir = getStagingDirectory().resolve("update-tx");
            Path manifestFile = txDir.resolve("manifest.json");
            Path sigFile = txDir.resolve("manifest.sig");

            if (!Files.exists(manifestFile) || !Files.exists(sigFile)) {
                System.err.println("REJECTED: Staged update missing manifest.json or manifest.sig");
                Files.deleteIfExists(handoffToken);
                return false;
            }

            // 1. Independent cryptographic verification in Launcher
            byte[] manifestBytes = Files.readAllBytes(manifestFile);
            String sigBase64 = Files.readString(sigFile, StandardCharsets.UTF_8).trim();
            String pubKeyBase64 = DEFAULT_PUBLIC_KEY_X509_BASE64;
            if (isTestModeAllowed()) {
                pubKeyBase64 = System.getProperty("app.update.public-key", DEFAULT_PUBLIC_KEY_X509_BASE64);
            }
            PublicKey pubKey = UpdateCrypto.decodePublicKey(pubKeyBase64);
            UpdateManifest manifest;
            try {
                manifest = UpdateManifest.parseAndVerify(manifestBytes, sigBase64, pubKey);
            } catch (Exception e) {
                System.err.println("SECURITY REJECTION: Launcher manifest signature verification failed: " + e.getMessage());
                Files.deleteIfExists(handoffToken);
                state.setStatus(UpdateState.STATUS_RECOVERY_REQUIRED);
                state.setErrorMessage("Launcher signature verification failed: " + e.getMessage());
                try { state.saveAtomic(stateFile); } catch (Exception ignored) {}
                return false;
            }

            // 2. Independent staged artifact verification
            Path stagedWar = txDir.resolve(manifest.getArtifactFilename());
            if (!Files.exists(stagedWar) || !Files.isRegularFile(stagedWar)) {
                if (Files.exists(txDir.resolve("rupeecrm-update.war"))) {
                    stagedWar = txDir.resolve("rupeecrm-update.war");
                } else if (Files.exists(txDir.resolve("rupeecrm.war"))) {
                    stagedWar = txDir.resolve("rupeecrm.war");
                } else if (state.getStagedArtifactPath() != null && Files.exists(Paths.get(state.getStagedArtifactPath()))) {
                    stagedWar = Paths.get(state.getStagedArtifactPath());
                } else {
                    System.err.println("REJECTED: Staged artifact not found: " + stagedWar);
                    Files.deleteIfExists(handoffToken);
                    return false;
                }
            }

            long stagedSize = Files.size(stagedWar);
            if (stagedSize != manifest.getArtifactSizeBytes()) {
                System.err.println("REJECTED: Staged size mismatch: " + stagedSize + " != " + manifest.getArtifactSizeBytes());
                Files.deleteIfExists(handoffToken);
                return false;
            }

            String stagedHash = UpdateCrypto.computeSha256(stagedWar);
            if (!manifest.getArtifactSha256().equalsIgnoreCase(stagedHash)) {
                System.err.println("REJECTED: Staged SHA-256 digest mismatch: " + stagedHash + " != " + manifest.getArtifactSha256());
                Files.deleteIfExists(handoffToken);
                return false;
            }

            if (!UpdateCrypto.isZipFile(stagedWar)) {
                System.err.println("REJECTED: Staged artifact failed ZIP magic check");
                Files.deleteIfExists(handoffToken);
                return false;
            }

            File currentWar = findCurrentWar();
            if (currentWar == null || !currentWar.exists()) {
                System.err.println("REJECTED: Current live WAR not found");
                return false;
            }

            // 3. Create and verify old WAR backup
            File backupWar = new File(currentWar.getParentFile(), currentWar.getName() + ".bak");
            Files.copy(currentWar.toPath(), backupWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (!backupWar.exists() || backupWar.length() != currentWar.length()) {
                System.err.println("REJECTED: Failed to create valid backup of current binary");
                return false;
            }

            updateTrayTooltip("RupeeCRM: Applying Application Update (v" + manifest.getAppVersion() + ")...");
            showTrayNotification("Updating RupeeCRM", "Applying new version v" + manifest.getAppVersion() + "...", TrayIcon.MessageType.INFO);

            state.setStatus(UpdateState.STATUS_ACTIVATION_STARTED);
            state.setBackupArtifactPath(backupWar.getAbsolutePath());
            state.setTargetVersion(manifest.getAppVersion());
            try { state.saveAtomic(stateFile); } catch (Exception ignored) {}

            // 4. Atomically replace WAR (with retries for Windows locks)
            boolean moved = false;
            for (int attempt = 1; attempt <= 5; attempt++) {
                try {
                    Files.move(stagedWar, currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    moved = true;
                    break;
                } catch (Exception e1) {
                    try {
                        Files.move(stagedWar, currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        moved = true;
                        break;
                    } catch (Exception e2) {
                        if (attempt == 5) throw e2;
                        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    }
                }
            }

            // Write version stamp for unambiguous verification
            try {
                if (currentWar.getParentFile() != null) {
                    Files.writeString(currentWar.getParentFile().toPath().resolve(".billsoft-version"), manifest.getAppVersion(), StandardCharsets.UTF_8);
                }
                Files.writeString(getDataDirectory().resolve(".billsoft-version"), manifest.getAppVersion(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}

            state.setStatus(UpdateState.STATUS_STARTING_NEW_VERSION);
            try { state.saveAtomic(stateFile); } catch (Exception ignored) {}

            // Delete handoff token
            Files.deleteIfExists(handoffToken);

            this.isCandidateLaunch = true;
            this.candidateVersion = manifest.getAppVersion();
            this.activeBackupWar = backupWar;

            System.out.println("Update successfully activated: " + currentWar.getAbsolutePath() + " (v" + candidateVersion + ")");
            return true;
        } catch (Exception e) {
            System.err.println("Error applying pending update: " + e.getMessage());
            return false;
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

    public static Path getBrowserProfileDirectory() {
        return getBaseDirectory().resolve("browser-profile");
    }

    public static Path getBaseDirectory() {
        String custom = System.getProperty("RUPEECRM_BASE_DIR");
        if (custom == null || custom.isBlank()) {
            custom = System.getenv("RUPEECRM_BASE_DIR");
        }
        if (custom != null && !custom.isBlank()) {
            return Paths.get(custom.trim());
        }

        String customData = System.getProperty("RUPEECRM_DATA_DIR");
        if (customData == null || customData.isBlank()) {
            customData = System.getenv("RUPEECRM_DATA_DIR");
        }
        if (customData == null || customData.isBlank()) {
            customData = System.getProperty("BILLSOFT_DATA_DIR");
        }
        if (customData == null || customData.isBlank()) {
            customData = System.getenv("BILLSOFT_DATA_DIR");
        }
        if (customData != null && !customData.isBlank()) {
            Path dataDir = Paths.get(customData.trim());
            if (dataDir.getFileName() != null && dataDir.getFileName().toString().equalsIgnoreCase("data") && dataDir.getParent() != null) {
                return dataDir.getParent();
            }
            return dataDir;
        }

        return resolveBaseDirectoryForOs(
                System.getProperty("os.name"),
                System.getenv("LOCALAPPDATA"),
                System.getProperty("user.home"));
    }

    public static Path resolveBaseDirectoryForOs(String osName, String localAppData, String userHome) {
        String os = (osName != null ? osName : "").toLowerCase();
        if (os.contains("win")) {
            if (localAppData != null && !localAppData.isBlank()) {
                return Paths.get(localAppData, "RupeeCRM");
            } else {
                return Paths.get(userHome != null ? userHome : System.getProperty("user.home"), ".rupeecrm");
            }
        } else if (os.contains("mac")) {
            return Paths.get(userHome != null ? userHome : System.getProperty("user.home"), "Library/Application Support/RupeeCRM");
        } else {
            return Paths.get(userHome != null ? userHome : System.getProperty("user.home"), ".rupeecrm");
        }
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
        // 1. Base Directory path (production / test environment)
        Path base = getBaseDirectory();
        File war = base.resolve("app").resolve("rupeecrm.war").toFile();
        if (war.exists() && war.length() > 0) return war;

        war = base.resolve("rupeecrm.war").toFile();
        if (war.exists() && war.length() > 0) return war;

        File appDir = getAppDirectory();

        // 2. Standard Production Installation Path: app/rupeecrm.war
        war = new File(appDir, "app" + File.separator + "rupeecrm.war");
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
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("RupeeCRM Desktop v1.0.0");
            return;
        }
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
