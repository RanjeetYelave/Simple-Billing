package com.billing.simple.launcher.update;

import com.billing.simple.launcher.LauncherMain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Launcher Real Subprocess & Process Termination Recovery Verification")
class LauncherRealSubprocessRecoveryTest {

    @TempDir
    Path tempDir;

    private final List<Process> spawnedProcesses = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(tempDir.resolve("app"));
        Files.createDirectories(tempDir.resolve("data"));
        Files.createDirectories(tempDir.resolve("staging"));
        Files.createDirectories(tempDir.resolve("logs"));
    }

    @AfterEach
    void tearDown() {
        for (Process p : spawnedProcesses) {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    private File createDummyWarWithMainClass(Path targetPath, String mainClassName) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClassName);

        try (OutputStream os = Files.newOutputStream(targetPath);
             JarOutputStream jos = new JarOutputStream(os, manifest)) {
            // Write a dummy entry to make the JAR valid and exceed minimum size
            jos.putNextEntry(new java.util.zip.ZipEntry("payload.dat"));
            jos.write(new byte[15_000]);
            jos.closeEntry();
        }
        return targetPath.toFile();
    }

    private Process startSupervisorSubprocess(Path baseDir) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = Paths.get(javaHome, "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        String classpath = System.getProperty("surefire.test.class.path");
        if (classpath == null || classpath.isBlank()) {
            classpath = System.getProperty("java.class.path");
        }

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classpath);
        command.add("-DRUPEECRM_BASE_DIR=" + baseDir.toAbsolutePath());
        command.add("-Djava.awt.headless=true");
        command.add("com.billing.simple.launcher.LauncherMain");
        command.add("--background");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(baseDir.toFile());
        pb.redirectErrorStream(true);
        File subLog = baseDir.resolve("logs/subprocess-supervisor.log").toFile();
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(subLog));

        Process p = pb.start();
        spawnedProcesses.add(p);
        return p;
    }

    @Test
    @DisplayName("Boundary 1: Real Subprocess Recovers Interrupted Swap (STATUS_ACTIVATION_STARTED) and Preserves DB")
    void testRealSubprocessRecoversInterruptedBinarySwap() throws Exception {
        // Setup initial files
        Path liveWar = tempDir.resolve("app/rupeecrm.war");
        Files.writeString(liveWar, "corrupted-partially-written-candidate-binary");

        Path backupWar = tempDir.resolve("app/rupeecrm.war.bak");
        Files.writeString(backupWar, "healthy-original-production-binary-v1.0.0");

        // Create representative database file in data directory
        Path dbFile = tempDir.resolve("data/database.mv.db");
        byte[] dbBytes = "SIMULATED_H2_DATABASE_WITH_CUSTOMER_INVOICES_AND_LEDGER_DATA".getBytes(StandardCharsets.UTF_8);
        Files.write(dbFile, dbBytes);

        // Persist interrupted state: supervisor was killed during binary replacement
        Path stateFile = tempDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setTargetVersion("1.1.0");
        state.setCurrentVersion("1.0.0");
        state.setStatus(UpdateState.STATUS_ACTIVATION_STARTED);
        state.setBackupArtifactPath(backupWar.toAbsolutePath().toString());
        state.saveAtomic(stateFile);

        // Spawn a real, separate JVM subprocess running LauncherMain
        Process supervisorProcess = startSupervisorSubprocess(tempDir);

        // Wait up to 8 seconds for supervisor to run recovery
        boolean stateUpdated = false;
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(stateFile)) {
                UpdateState s = UpdateState.load(stateFile);
                if (s != null && UpdateState.STATUS_ROLLED_BACK.equals(s.getStatus())) {
                    stateUpdated = true;
                    break;
                }
            }
            Thread.sleep(200);
        }

        // Forcibly terminate the supervisor subprocess
        supervisorProcess.destroyForcibly();
        supervisorProcess.waitFor(5, TimeUnit.SECONDS);

        // Assert outcomes performed by the independent external JVM
        Path subLog = tempDir.resolve("logs/subprocess-supervisor.log");
        String logContent = Files.exists(subLog) ? Files.readString(subLog) : "<no log>";
        assertTrue(stateUpdated, "External supervisor subprocess must transition state to STATUS_ROLLED_BACK. Subprocess log:\n" + logContent);
        assertEquals("healthy-original-production-binary-v1.0.0", Files.readString(liveWar),
                "External supervisor must restore healthy binary from backup");

        // Verify customer database preservation
        assertTrue(Files.exists(dbFile), "Customer database must exist");
        assertArrayEquals(dbBytes, Files.readAllBytes(dbFile), "Customer database must not be modified or truncated");
    }

    @Test
    @DisplayName("Boundary 2: Supervisor Process Forcibly Killed (SIGKILL) Mid-Transaction; Second Instance Recovers")
    void testSupervisorForciblyKilledMidTransactionAndSecondSubprocessRecovers() throws Exception {
        Path liveWar = tempDir.resolve("app/rupeecrm.war");
        Files.writeString(liveWar, "active-v1.0.0");

        Path backupWar = tempDir.resolve("app/rupeecrm.war.bak");
        Files.writeString(backupWar, "active-v1.0.0");

        Path dbFile = tempDir.resolve("data/database.mv.db");
        byte[] dbBytes = "REPRESENTATIVE_CUSTOMER_DATA_BEFORE_KILL".getBytes(StandardCharsets.UTF_8);
        Files.write(dbFile, dbBytes);

        // 1. Start first supervisor subprocess
        Process firstSupervisor = startSupervisorSubprocess(tempDir);
        assertTrue(firstSupervisor.isAlive(), "First supervisor subprocess must start");

        // 2. Simulate interruption mid-transaction: write ACTIVATION_STARTED and abruptly destroy first supervisor
        Path stateFile = tempDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setTargetVersion("1.2.0");
        state.setCurrentVersion("1.0.0");
        state.setStatus(UpdateState.STATUS_ACTIVATION_STARTED);
        state.setBackupArtifactPath(backupWar.toAbsolutePath().toString());
        state.saveAtomic(stateFile);

        // Forcibly kill first supervisor (simulating sudden crash / SIGKILL / power loss)
        firstSupervisor.destroyForcibly();
        firstSupervisor.waitFor(5, TimeUnit.SECONDS);
        assertFalse(firstSupervisor.isAlive(), "First supervisor must be dead");

        // Intentionally damage live binary to simulate partial write before kill
        Files.writeString(liveWar, "damaged-binary-bytes");

        // 3. Start a fresh, separate second supervisor subprocess on the same directory
        Process secondSupervisor = startSupervisorSubprocess(tempDir);

        // Wait for second supervisor to recover the interrupted transaction
        boolean recovered = false;
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(stateFile)) {
                UpdateState s = UpdateState.load(stateFile);
                if (s != null && UpdateState.STATUS_ROLLED_BACK.equals(s.getStatus())) {
                    recovered = true;
                    break;
                }
            }
            Thread.sleep(200);
        }

        secondSupervisor.destroyForcibly();
        secondSupervisor.waitFor(5, TimeUnit.SECONDS);

        Path subLog = tempDir.resolve("logs/subprocess-supervisor.log");
        String logContent = Files.exists(subLog) ? Files.readString(subLog) : "<no log>";
        assertTrue(recovered, "Second supervisor subprocess must recover the interrupted transaction. Subprocess log:\n" + logContent);
        assertEquals("active-v1.0.0", Files.readString(liveWar), "Second supervisor must restore the binary from .bak");
        assertArrayEquals(dbBytes, Files.readAllBytes(dbFile), "Customer database must be 100% preserved");
    }

    @Test
    @DisplayName("Boundary 3: Real Subprocess Recovers Interrupted Rollback (STATUS_ROLLBACK_IN_PROGRESS)")
    void testRealSubprocessRecoversInterruptedRollback() throws Exception {
        Path liveWar = tempDir.resolve("app/rupeecrm.war");
        Files.writeString(liveWar, "half-restored-bad-war");

        Path backupWar = tempDir.resolve("app/rupeecrm.war.bak");
        Files.writeString(backupWar, "good-backup-v1.0.0");

        Path stateFile = tempDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setTargetVersion("1.2.0");
        state.setCurrentVersion("1.0.0");
        state.setStatus(UpdateState.STATUS_ROLLBACK_IN_PROGRESS);
        state.setBackupArtifactPath(backupWar.toAbsolutePath().toString());
        state.saveAtomic(stateFile);

        Process supervisor = startSupervisorSubprocess(tempDir);

        boolean recovered = false;
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            UpdateState s = UpdateState.load(stateFile);
            if (s != null && UpdateState.STATUS_ROLLED_BACK.equals(s.getStatus())) {
                recovered = true;
                break;
            }
            Thread.sleep(200);
        }

        supervisor.destroyForcibly();
        supervisor.waitFor(5, TimeUnit.SECONDS);

        Path subLog = tempDir.resolve("logs/subprocess-supervisor.log");
        String logContent = Files.exists(subLog) ? Files.readString(subLog) : "<no log>";
        assertTrue(recovered, "Supervisor subprocess must complete interrupted rollback. Subprocess log:\n" + logContent);
        assertEquals("good-backup-v1.0.0", Files.readString(liveWar), "Live binary must be restored");
    }

    @Test
    @DisplayName("Boundary 4: Candidate Process Startup Failure Causes Supervisor Automatic Rollback")
    void testCandidateCrashTriggersAutomaticSupervisorRollback() throws Exception {
        // Create a live candidate WAR that will fail to start because Main-Class does not exist
        Path liveWar = tempDir.resolve("app/rupeecrm.war");
        createDummyWarWithMainClass(liveWar, "com.billing.simple.NonExistentMain");

        Path backupWar = tempDir.resolve("app/rupeecrm.war.bak");
        createDummyWarWithMainClass(backupWar, "com.billing.simple.WorkingMain");

        // Write candidate launch state
        Path stateFile = tempDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setTargetVersion("1.5.0");
        state.setCurrentVersion("1.0.0");
        state.setStatus(UpdateState.STATUS_STARTING_NEW_VERSION);
        state.setBackupArtifactPath(backupWar.toAbsolutePath().toString());
        state.saveAtomic(stateFile);

        // Spawn supervisor subprocess
        Process supervisor = startSupervisorSubprocess(tempDir);

        // Supervisor resumes candidate tracking, attempts to launch candidate, candidate fails,
        // supervisor initiates rollback to .bak, and updates status to STATUS_ROLLED_BACK
        boolean rolledBack = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            UpdateState s = UpdateState.load(stateFile);
            if (s != null && UpdateState.STATUS_ROLLED_BACK.equals(s.getStatus())) {
                rolledBack = true;
                break;
            }
            Thread.sleep(300);
        }

        supervisor.destroyForcibly();
        supervisor.waitFor(5, TimeUnit.SECONDS);

        Path subLog = tempDir.resolve("logs/subprocess-supervisor.log");
        String logContent = Files.exists(subLog) ? Files.readString(subLog) : "<no log>";
        assertTrue(rolledBack, "Supervisor must detect candidate failure and complete automatic rollback to STATUS_ROLLED_BACK. Subprocess log:\n" + logContent);
        assertEquals(Files.size(backupWar), Files.size(liveWar), "Live binary must match backup binary size");
    }
}
