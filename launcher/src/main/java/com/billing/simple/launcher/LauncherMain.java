package com.billing.simple.launcher;

import java.io.File;
import java.io.IOException;
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

    private static final String APP_DIR = System.getProperty("user.dir");

    public static void main(String[] args) {
        System.out.println("Starting Simple Billing Launcher...");
        System.out.println("App Directory: " + APP_DIR);

        LauncherMain launcher = new LauncherMain();
        launcher.runLoop();
    }

    private void runLoop() {
        while (true) {
            File shellJar = findShellJar();
            if (shellJar == null || !shellJar.exists()) {
                System.err.println("Fatal: JavaFX Shell jar not found.");
                break;
            }

            System.out.println("Launching shell: " + shellJar.getAbsolutePath());
            Process process = null;
            try {
                process = launchProcess(shellJar);
            } catch (IOException e) {
                System.err.println("Failed to launch shell process: " + e.getMessage());
                break;
            }

            // Monitor boot for 25 seconds
            boolean bootedSuccessfully = monitorBoot(process, 25000);

            if (!bootedSuccessfully) {
                System.err.println("Boot failure detected! Initiating rollback...");
                handleBootFailure(process);
                // After rollback, the loop will re-launch the restored version in the next iteration.
                continue;
            }

            // If it booted successfully, wait for it to exit normally
            int exitCode = -1;
            try {
                exitCode = process.waitFor();
                System.out.println("Shell exited with code: " + exitCode);
            } catch (InterruptedException e) {
                System.err.println("Launcher interrupted while waiting for shell: " + e.getMessage());
                break;
            }

            // Check if there is an update pending
            File updateWar = findUpdateWar();
            if (updateWar != null && updateWar.exists() && updateWar.length() > 0) {
                System.out.println("Pending update found at: " + updateWar.getAbsolutePath());
                boolean prepared = prepareAndApplyUpdate(updateWar);
                if (prepared) {
                    System.out.println("Update applied successfully. Restarting...");
                    // Continue loop to start the updated app
                    continue;
                } else {
                    System.err.println("Failed to apply update.");
                }
            }

            // Normal exit, break out of loop
            break;
        }
        System.out.println("Launcher exiting.");
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
        // Check relative to appDir
        File file = new File(appDir, relativePath);
        if (file.exists()) return file;
        
        // Check relative to appDir's parent (useful if launcher is run inside app/ subfolder or launcher/target/)
        file = new File(appDir.getParentFile(), relativePath);
        if (file.exists()) return file;

        // Check relative to appDir's grandparent (useful if launcher is inside launcher/target/)
        if (appDir.getParentFile() != null) {
            file = new File(appDir.getParentFile().getParentFile(), relativePath);
            if (file.exists()) return file;
        }

        // Fallback to user.dir
        return new File(relativePath);
    }

    private File findShellJar() {
        File shell = resolveFile("shell.jar");
        if (shell.exists()) return shell;

        shell = resolveFile("shell/target/shell-0.0.1-SNAPSHOT.jar");
        if (shell.exists()) return shell;

        shell = resolveFile("../shell/target/shell-0.0.1-SNAPSHOT.jar");
        if (shell.exists()) return shell;

        return null;
    }

    private File findUpdateWar() {
        File update = resolveFile("runtime/billsoft-update.war");
        if (update.exists()) return update;

        update = resolveFile("billsoft/target/billsoft-update.war");
        if (update.exists()) return update;

        update = resolveFile("../billsoft/target/billsoft-update.war");
        if (update.exists()) return update;

        return null;
    }

    private File findCurrentWar() {
        File current = resolveFile("runtime/billsoft.war");
        if (current.exists()) return current;

        current = resolveFile("billsoft/target/billsoft-0.0.1-SNAPSHOT.war");
        if (current.exists()) return current;

        current = resolveFile("../billsoft/target/billsoft-0.0.1-SNAPSHOT.war");
        if (current.exists()) return current;

        return null;
    }

    private Process launchProcess(File jarFile) throws IOException {
        List<String> command = new ArrayList<>();
        
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + (System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        
        File javaBinFile = new File(javaBin);
        if (javaBinFile.exists()) {
            command.add(javaBinFile.getAbsolutePath());
        } else {
            command.add("java"); // fallback
        }
        command.add("-jar");
        command.add(jarFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            Path logsDir = getDataDirectory().resolve("logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }
            pb.redirectOutput(ProcessBuilder.Redirect.to(logsDir.resolve("shell-stdout.log").toFile()));
            pb.redirectError(ProcessBuilder.Redirect.to(logsDir.resolve("shell-stderr.log").toFile()));
        } catch (Exception e) {
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        }
        return pb.start();
    }

    private boolean monitorBoot(Process process, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!process.isAlive()) {
                // If it exited with a non-zero code or exited early, it's a crash
                int exitVal = process.exitValue();
                System.err.println("Process terminated early during boot with exit code: " + exitVal);
                return false;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true; // Still alive after timeout window, assume healthy
    }

    private boolean prepareAndApplyUpdate(File updateWar) {
        File currentWar = findCurrentWar();
        if (currentWar == null || !currentWar.exists()) {
            System.err.println("Current WAR not found. Cannot update.");
            return false;
        }

        Path dataDir = getDataDirectory();
        System.out.println("User Data Directory: " + dataDir);

        // 1. Unconditional Database Backup
        backupDatabase(dataDir);

        // 2. Backup Current WAR
        File backupWar = getBackupWarPath(currentWar);
        try {
            System.out.println("Backing up current WAR to: " + backupWar.getAbsolutePath());
            Files.copy(currentWar.toPath(), backupWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to backup current WAR: " + e.getMessage());
            return false;
        }

        // 3. Overwrite current WAR with update WAR
        try {
            System.out.println("Replacing current WAR with updated version...");
            Files.copy(updateWar.toPath(), currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(updateWar.toPath());
            return true;
        } catch (IOException e) {
            System.err.println("Failed to replace WAR file: " + e.getMessage());
            // Attempt to restore from the backup we just made
            try {
                Files.copy(backupWar.toPath(), currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.err.println("Critical: Failed to restore WAR backup: " + ex.getMessage());
            }
            return false;
        }
    }

    private void handleBootFailure(Process failedProcess) {
        // Stop failed process just in case
        if (failedProcess.isAlive()) {
            failedProcess.destroy();
            try {
                failedProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }

        File currentWar = findCurrentWar();
        if (currentWar == null) return;

        File backupWar = getBackupWarPath(currentWar);
        Path dataDir = getDataDirectory();

        System.out.println("Initiating database and WAR rollback...");

        // 1. Rollback Database
        rollbackDatabase(dataDir);

        // 2. Rollback WAR
        if (backupWar.exists()) {
            try {
                System.out.println("Restoring WAR from backup...");
                Files.copy(backupWar.toPath(), currentWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(backupWar.toPath());
            } catch (IOException e) {
                System.err.println("Failed to restore WAR from backup: " + e.getMessage());
            }
        }
    }

    private Path getDataDirectory() {
        String envPath = System.getenv("BILLSOFT_DATA_DIR");
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
        if (!Files.exists(dbFile)) {
            System.out.println("No database file found at " + dbFile.toAbsolutePath() + ", skipping backup.");
            return;
        }

        Path backupDir = dataDir.resolve("backup");
        try {
            Files.createDirectories(backupDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupFile = backupDir.resolve("database_" + timestamp + ".mv.db");
            System.out.println("Backing up database to: " + backupFile.toAbsolutePath());
            Files.copy(dbFile, backupFile, StandardCopyOption.REPLACE_EXISTING);

            // Also keep reference to the latest backup for immediate rollback support
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
                System.out.println("Restoring database from latest backup...");
                Files.copy(latestBackup, dbFile, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(latestBackup);
            } catch (IOException e) {
                System.err.println("Failed to restore database from backup: " + e.getMessage());
            }
        } else {
            System.out.println("No latest database backup file found to restore.");
        }
    }

    private File getBackupWarPath(File currentWar) {
        return new File(currentWar.getParentFile(), "billsoft-backup.war");
    }
}
