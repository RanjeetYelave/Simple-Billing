package com.billing.simple.billsoft.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DevLogService {

    private static final Logger log = LoggerFactory.getLogger(DevLogService.class);
    private static final String APPENDER_NAME = "DEV_FILE_APPENDER";
    private final AtomicBoolean devLogsEnabled = new AtomicBoolean(false);
    private final Path logFilePath;

    public DevLogService() {
        this.logFilePath = resolveLogFilePath();
        // Check if logs directory exists, otherwise create
        try {
            if (this.logFilePath.getParent() != null) {
                Files.createDirectories(this.logFilePath.getParent());
            }
        } catch (Exception e) {
            log.warn("Failed to create log directories: {}", e.getMessage());
        }
    }

    private Path resolveLogFilePath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String appData = System.getenv("APPDATA");

        if (os.contains("win") && appData != null && !appData.isBlank()) {
            return Paths.get(appData, "RupeeCRM", "logs", "developer-debug.log");
        }

        String userHome = System.getProperty("user.home", ".");
        return Paths.get(userHome, ".rupeecrm", "logs", "developer-debug.log");
    }

    public synchronized Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", devLogsEnabled.get());
        status.put("logFilePath", logFilePath.toAbsolutePath().toString());

        File f = logFilePath.toFile();
        if (f.exists()) {
            status.put("exists", true);
            status.put("sizeBytes", f.length());
            status.put("sizeFormatted", String.format("%.2f KB", f.length() / 1024.0));
            try {
                List<String> allLines = Files.readAllLines(logFilePath, StandardCharsets.UTF_8);
                int count = allLines.size();
                int from = Math.max(0, count - 100);
                status.put("recentLines", allLines.subList(from, count));
            } catch (Exception e) {
                status.put("recentLines", List.of("Error reading log file: " + e.getMessage()));
            }
        } else {
            status.put("exists", false);
            status.put("sizeBytes", 0);
            status.put("sizeFormatted", "0 KB");
            status.put("recentLines", List.of());
        }

        return status;
    }

    public synchronized Map<String, Object> setEnabled(boolean enable) {
        devLogsEnabled.set(enable);
        configureLogback(enable);

        // Write diagnostic banner when enabled
        if (enable) {
            writeDiagnosticHeader();
        }

        log.info("Developer debug logging has been {}", enable ? "ENABLED" : "DISABLED");
        return getStatus();
    }

    private void configureLogback(boolean enable) {
        try {
            if (LoggerFactory.getILoggerFactory() instanceof LoggerContext) {
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                ch.qos.logback.classic.Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
                ch.qos.logback.classic.Logger appLogger = context.getLogger("com.billing.simple");
                ch.qos.logback.classic.Logger hibernateLogger = context.getLogger("org.hibernate.SQL");
                ch.qos.logback.classic.Logger springLogger = context.getLogger("org.springframework.web");

                if (enable) {
                    rootLogger.setLevel(Level.INFO);
                    appLogger.setLevel(Level.DEBUG);
                    hibernateLogger.setLevel(Level.DEBUG);
                    springLogger.setLevel(Level.DEBUG);

                    // Add file appender if not already attached
                    if (rootLogger.getAppender(APPENDER_NAME) == null) {
                        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
                        fileAppender.setName(APPENDER_NAME);
                        fileAppender.setContext(context);
                        fileAppender.setFile(logFilePath.toAbsolutePath().toString());
                        fileAppender.setAppend(true);

                        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
                        encoder.setContext(context);
                        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
                        encoder.start();

                        fileAppender.setEncoder(encoder);
                        fileAppender.start();

                        rootLogger.addAppender(fileAppender);
                    }
                } else {
                    appLogger.setLevel(Level.INFO);
                    hibernateLogger.setLevel(Level.WARN);
                    springLogger.setLevel(Level.INFO);

                    // Stop and remove file appender
                    FileAppender<ILoggingEvent> appender = (FileAppender<ILoggingEvent>) rootLogger.getAppender(APPENDER_NAME);
                    if (appender != null) {
                        appender.stop();
                        rootLogger.detachAppender(APPENDER_NAME);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not dynamically adjust Logback levels: {}", e.getMessage());
        }
    }

    private void writeDiagnosticHeader() {
        try {
            File f = logFilePath.toFile();
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(f, true))) {
                writer.println();
                writer.println("================================================================================");
                writer.println(" [RUPEECRM DEVELOPER DEBUG LOG SESSION]");
                writer.println(" Started at: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                writer.println(" OS: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
                writer.println(" Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
                writer.println(" Working Dir: " + System.getProperty("user.dir"));
                writer.println(" Free Memory: " + (Runtime.getRuntime().freeMemory() / (1024 * 1024)) + " MB / " + (Runtime.getRuntime().totalMemory() / (1024 * 1024)) + " MB");
                writer.println("================================================================================");
                writer.println();
            }
        } catch (Exception e) {
            log.warn("Failed to write diagnostic header: {}", e.getMessage());
        }
    }

    public Path getLogFilePath() {
        return logFilePath;
    }
}
