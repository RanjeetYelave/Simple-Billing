package com.billing.simple.shell;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ShellMain extends Application {
    private Process backendProcess;
    private int port;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopBackend));
        try {
            port = PortUtil.findFreePort();
            startBackend();
            // Wait for backend to become reachable (simple poll)
            waitForBackend();
        } catch (Exception e) {
            e.printStackTrace();
            Platform.exit();
            return;
        }

        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        String url = "http://localhost:" + port + "/"; // root context of the WAR
        engine.load(url);

        BorderPane root = new BorderPane(webView);
        Scene scene = new Scene(root, 1024, 768);
        primaryStage.setTitle("Simple Billing");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            stopBackend();
            Platform.exit();
        });
    }

    private File getAppDirectory() {
        try {
            File codeSourceFile = new File(ShellMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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
        
        // Check relative to appDir's parent (useful if shell is run inside app/ subfolder or shell/target/)
        file = new File(appDir.getParentFile(), relativePath);
        if (file.exists()) return file;

        // Check relative to appDir's grandparent (useful if shell is inside shell/target/)
        if (appDir.getParentFile() != null) {
            file = new File(appDir.getParentFile().getParentFile(), relativePath);
            if (file.exists()) return file;
        }

        // Fallback to user.dir
        return new File(relativePath);
    }

    private void startBackend() throws IOException {
        File warFile = resolveFile("runtime/billsoft.war");
        if (!warFile.exists()) {
            warFile = resolveFile("billsoft/target/billsoft-0.0.1-SNAPSHOT.war");
        }
        if (!warFile.exists()) {
            warFile = resolveFile("../billsoft/target/billsoft-0.0.1-SNAPSHOT.war");
        }
        if (!warFile.exists()) {
            throw new IOException("WAR file not found at any expected location (runtime/billsoft.war, billsoft/target/, or ../billsoft/target/)");
        }

        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + (System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");

        List<String> command = new ArrayList<>();
        File javaBinFile = new File(javaBin);
        if (javaBinFile.exists()) {
            command.add(javaBinFile.getAbsolutePath());
        } else {
            command.add("java"); // fallback
        }
        command.add("-jar");
        command.add(warFile.getAbsolutePath());
        command.add("--server.port=" + port);

        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            File logsDir = new File(getDataDirectory(), "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            pb.redirectOutput(ProcessBuilder.Redirect.to(new File(logsDir, "backend-stdout.log")));
            pb.redirectError(ProcessBuilder.Redirect.to(new File(logsDir, "backend-stderr.log")));
        } catch (Exception e) {
            pb.redirectErrorStream(true);
        }
        backendProcess = pb.start();
    }

    private File getDataDirectory() {
        String envPath = System.getenv("BILLSOFT_DATA_DIR");
        if (envPath != null && !envPath.trim().isEmpty()) {
            return new File(envPath.trim());
        }
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                return new File(appData, "SimpleBilling");
            }
        } else if (os.contains("mac")) {
            return new File(System.getProperty("user.home"), "Library/Application Support/SimpleBilling");
        }
        return new File(System.getProperty("user.home"), ".simplebilling");
    }

    private void waitForBackend() throws InterruptedException {
        // Simple poll up to 30 seconds
        int attempts = 30;
        while (attempts-- > 0) {
            if (isBackendUp()) {
                return;
            }
            TimeUnit.SECONDS.sleep(1);
        }
        System.err.println("Backend did not become reachable in time.");
    }

    private boolean isBackendUp() {
        try {
            URL url = new URL("http://localhost:" + port + "/api/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void stopBackend() {
        if (backendProcess != null && backendProcess.isAlive()) {
            backendProcess.destroy();
            try {
                backendProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }
    }
}
