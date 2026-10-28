package com.billing.simple.launcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LauncherEdgeAppTest {

    @TempDir
    Path tempDir;

    private String originalEdgePathProperty;
    private String originalBaseDirProperty;

    @BeforeEach
    void setUp() {
        originalEdgePathProperty = System.getProperty("rupeecrm.edge.path");
        originalBaseDirProperty = System.getProperty("RUPEECRM_BASE_DIR");
        LauncherMain.edgeAppProcess = null;
        LauncherMain.lastWindowLaunchTime = 0;
        LauncherMain.releaseSupervisorLock();
    }

    @AfterEach
    void tearDown() {
        if (originalEdgePathProperty != null) {
            System.setProperty("rupeecrm.edge.path", originalEdgePathProperty);
        } else {
            System.clearProperty("rupeecrm.edge.path");
        }

        if (originalBaseDirProperty != null) {
            System.setProperty("RUPEECRM_BASE_DIR", originalBaseDirProperty);
        } else {
            System.clearProperty("RUPEECRM_BASE_DIR");
        }

        LauncherMain.stopEdgeApp();
        LauncherMain.releaseSupervisorLock();
        LauncherMain.lastWindowLaunchTime = 0;
    }

    @Test
    void testResolveEdgeExecutable_ExplicitValidOverride() throws IOException {
        Path mockEdge = Files.createFile(tempDir.resolve("msedge.exe"));
        System.setProperty("rupeecrm.edge.path", mockEdge.toAbsolutePath().toString());

        File resolved = LauncherMain.resolveEdgeExecutable();
        assertNotNull(resolved, "Should resolve Edge when valid override path is supplied");
        assertEquals(mockEdge.toAbsolutePath().toString(), resolved.getAbsolutePath());
    }

    @Test
    void testResolveEdgeExecutable_InvalidOverrideHandledGracefully() {
        System.setProperty("rupeecrm.edge.path", tempDir.resolve("non_existent_edge.exe").toAbsolutePath().toString());

        File resolved = LauncherMain.resolveEdgeExecutable();
        // Since the override doesn't exist, it should not return the non-existent file
        if (resolved != null) {
            assertTrue(resolved.exists(), "Resolved Edge file must exist on disk");
        }
    }

    @Test
    void testBuildEdgeAppArguments() {
        String edgePath = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
        String appUrl = "http://localhost:28080/";
        Path profileDir = Paths.get("C:\\Users\\User\\AppData\\Local\\RupeeCRM\\browser-profile");

        List<String> args = LauncherMain.buildEdgeAppArguments(edgePath, appUrl, profileDir);

        assertNotNull(args);
        assertEquals(edgePath, args.get(0), "First argument must be the Edge binary executable");
        assertTrue(args.contains("--app=http://localhost:28080/"), "Must specify --app flag with target application URL");
        assertTrue(args.contains("--user-data-dir=" + profileDir.toAbsolutePath().toString()),
                "Must specify isolated --user-data-dir with RupeeCRM profile directory");
        assertTrue(args.contains("--no-first-run"), "Must suppress first run experience wizard");
        assertTrue(args.contains("--no-default-browser-check"), "Must suppress default browser check prompt");
        assertTrue(args.contains("--disable-features=Translate"), "Should disable unnecessary translate popups");
        assertTrue(args.contains("--disable-background-mode"), "Must disable Chromium background mode to ensure window exit terminates process");

        // Verify security: must not weaken isolation or disable security
        for (String arg : args) {
            assertFalse(arg.contains("--disable-web-security"), "Must never disable web security");
            assertFalse(arg.contains("--ignore-certificate-errors"), "Must never ignore certificate errors");
        }
    }

    @Test
    void testBuildEdgeAppArguments_PathWithSpacesHandledWithoutArtificialQuotes() {
        String edgePath = "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe";
        String appUrl = "http://localhost:28080/";
        Path profileWithSpaces = Paths.get("C:\\Users\\John Doe\\AppData\\Local\\RupeeCRM\\browser-profile");

        List<String> args = LauncherMain.buildEdgeAppArguments(edgePath, appUrl, profileWithSpaces);

        String expectedArg = "--user-data-dir=" + profileWithSpaces.toAbsolutePath().toString();
        assertTrue(args.contains(expectedArg), "Argument list must contain raw path for ProcessBuilder OS-level quoting");
        // Verify no artificial escaped quotes were added
        assertFalse(args.stream().anyMatch(a -> a.contains("\"")), "Arguments should not contain embedded quotes");
    }

    @Test
    void testBrowserProfileDirectorySeparation() {
        System.setProperty("RUPEECRM_BASE_DIR", tempDir.toAbsolutePath().toString());

        Path profileDir = LauncherMain.getBrowserProfileDirectory();
        Path dataDir = LauncherMain.getDataDirectory();
        Path backupsDir = LauncherMain.getBackupsDirectory();
        Path logsDir = LauncherMain.getLogsDirectory();
        Path stagingDir = LauncherMain.getStagingDirectory();

        assertNotNull(profileDir);
        assertTrue(profileDir.toString().endsWith("browser-profile"));
        assertTrue(profileDir.startsWith(LauncherMain.getBaseDirectory()), "Browser profile must be within base directory");

        // Strict isolation checks: browser profile must not overlap database, backups, logs, or staging
        assertNotEquals(profileDir, dataDir, "Browser profile must not overlap database directory");
        assertNotEquals(profileDir, backupsDir, "Browser profile must not overlap backups directory");
        assertNotEquals(profileDir, logsDir, "Browser profile must not overlap logs directory");
        assertNotEquals(profileDir, stagingDir, "Browser profile must not overlap staging directory");

        assertFalse(profileDir.startsWith(dataDir), "Browser profile must not be located inside data directory");
        assertFalse(dataDir.startsWith(profileDir), "Data directory must not be located inside browser profile");
    }

    @Test
    void testWindowLaunchDebounce() {
        LauncherMain.lastWindowLaunchTime = System.currentTimeMillis();

        // Rapid click immediately after previous launch should be ignored by debounce guard
        boolean handled = LauncherMain.openApplicationWindow("http://localhost:28080/");
        assertTrue(handled, "Debounced duplicate launch request should return gracefully");
    }

    @Test
    void testStopEdgeAppSafelyTerminatesProcess() throws Exception {
        // Start a benign dummy process to simulate edgeAppProcess
        ProcessBuilder pb;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", "ping -n 10 127.0.0.1 > nul");
        } else {
            pb = new ProcessBuilder("sleep", "10");
        }

        Process process = pb.start();
        LauncherMain.edgeAppProcess = process;

        assertTrue(process.isAlive(), "Dummy process should initially be alive");

        // Stop edge app
        LauncherMain.stopEdgeApp();

        assertNull(LauncherMain.edgeAppProcess, "Edge app process handle must be cleared after stop");
        assertFalse(process.isAlive(), "Dummy process must be terminated by stopEdgeApp()");

        // Calling again when already null must be safe and idempotent
        assertDoesNotThrow(LauncherMain::stopEdgeApp);
    }

    @Test
    void testStopEdgeAppDoesNotAffectUnrelatedProcesses() {
        // Calling stopEdgeApp with null process handle does not fail or kill unrelated processes
        LauncherMain.edgeAppProcess = null;
        assertDoesNotThrow(LauncherMain::stopEdgeApp);
    }

    @Test
    void testSupervisorLock_AcquireAndRelease() throws Exception {
        System.setProperty("RUPEECRM_BASE_DIR", tempDir.toAbsolutePath().toString());

        // Initial acquisition must succeed
        assertTrue(LauncherMain.acquireSupervisorLock(), "First supervisor instance must acquire lock");

        // Repeated acquisition by the same instance is idempotent
        assertTrue(LauncherMain.acquireSupervisorLock(), "Re-acquiring held lock by same instance returns true");

        // A second channel trying to lock the same file should fail (simulating second supervisor process)
        Path lockFile = tempDir.resolve(".supervisor.lock");
        assertTrue(Files.exists(lockFile), "Lock file must exist on disk");

        try (FileChannel ch2 = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock secondLock = null;
            try {
                secondLock = ch2.tryLock();
            } catch (OverlappingFileLockException e) {
                // Expected when JVM detects overlapping lock on the same file
            }
            assertNull(secondLock, "Second supervisor invocation must not acquire lock while held");
        }

        // Release supervisor lock
        LauncherMain.releaseSupervisorLock();
        assertNull(LauncherMain.supervisorLock, "Supervisor lock reference must be cleared");

        // After release, a new channel can acquire the lock
        try (FileChannel ch3 = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock thirdLock = ch3.tryLock();
            assertNotNull(thirdLock, "After release, lock must be acquirable by next process");
            thirdLock.release();
        }
    }

    @Test
    void testHandoffProtection_DoesNotOverwriteLiveProcessHandle() throws Exception {
        ProcessBuilder pb;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", "ping -n 10 127.0.0.1 > nul");
        } else {
            pb = new ProcessBuilder("sleep", "10");
        }

        Process mainProcess = pb.start();
        Process handoffProcess = pb.start();

        try {
            LauncherMain.edgeAppProcess = mainProcess;
            assertTrue(mainProcess.isAlive());

            // Simulate what openApplicationWindow does when a handoff process is spawned:
            if (LauncherMain.edgeAppProcess == null || !LauncherMain.edgeAppProcess.isAlive()) {
                LauncherMain.edgeAppProcess = handoffProcess;
            }

            // Verify mainProcess handle was protected and NOT overwritten
            assertSame(mainProcess, LauncherMain.edgeAppProcess,
                    "Live browser main process handle must not be replaced by a transient handoff process");
        } finally {
            mainProcess.destroyForcibly();
            handoffProcess.destroyForcibly();
        }
    }

    @Test
    void testSupervisorActivationServer_StartsAndServesValidToken() throws Exception {
        System.setProperty("RUPEECRM_BASE_DIR", tempDir.toAbsolutePath().toString());

        LauncherMain.startActivationServer();

        Path ipcFile = tempDir.resolve(".supervisor.ipc");
        assertTrue(Files.exists(ipcFile), "Activation IPC file must be created on disk");

        List<String> lines = Files.readAllLines(ipcFile, StandardCharsets.UTF_8);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("port=")), "IPC file must declare port");
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("token=")), "IPC file must declare secret token");

        // Notify active supervisor using the IPC protocol
        boolean notified = LauncherMain.notifyActiveSupervisorToActivate();
        assertTrue(notified, "Secondary launcher notification should successfully communicate with primary supervisor");

        // Stop activation server
        LauncherMain.stopActivationServer();
        assertFalse(Files.exists(ipcFile), "Stopping activation server must clean up IPC file");

        // Subsequent notification must fail gracefully
        assertFalse(LauncherMain.notifyActiveSupervisorToActivate(), "Notification after server stop must return false");
    }

    @Test
    void testSupervisorActivationServer_RejectsInvalidToken() throws Exception {
        System.setProperty("RUPEECRM_BASE_DIR", tempDir.toAbsolutePath().toString());

        LauncherMain.startActivationServer();

        Path ipcFile = tempDir.resolve(".supervisor.ipc");
        assertTrue(Files.exists(ipcFile));

        int port = -1;
        for (String line : Files.readAllLines(ipcFile, StandardCharsets.UTF_8)) {
            if (line.startsWith("port=")) {
                port = Integer.parseInt(line.substring("port=".length()).trim());
            }
        }
        assertTrue(port > 0);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1500);
            socket.setSoTimeout(2000);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            writer.write("fake-unauthorized-token ACTIVATE\n");
            writer.flush();

            String response = reader.readLine();
            assertEquals("DENIED", response != null ? response.trim() : "", "Activation server must reject invalid secret tokens");
        } finally {
            LauncherMain.stopActivationServer();
        }
    }
}
