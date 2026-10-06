package com.billing.simple.billsoft.recovery;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.billing.simple.billsoft.service.DevLogService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class DiagnosticLogService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticLogService.class);
    private static final String APPENDER_NAME = "DIAGNOSTIC_LIVE_RING_APPENDER";
    private static final int MAX_BUFFER_CAPACITY = 2000;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Sensitive pattern sanitization: master keys, passwords, bearer tokens, JWTs, session IDs, credit cards
    private static final Pattern MASTER_KEY_PATTERN = Pattern.compile("(?i)Saidarshan\\*1");
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i)(\"?)(password|pwd|secret|masterKey|masterPassword|diagnosticKey|clearanceKey|token|apiKey|authorization)(\"?)\\s*[:=]\\s*\"?([^\"\\s,;}{]+)\"?"
    );
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(bearer\\s+)[a-zA-Z0-9._\\-]+");
    private static final Pattern JWT_PATTERN = Pattern.compile("\\beyJ[a-zA-Z0-9_\\-]{10,}\\.[a-zA-Z0-9_\\-]{10,}\\.[a-zA-Z0-9_\\-]+\\b");
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("(?i)(jsessionid=|rupee_diag_session=)[a-zA-Z0-9._\\-]+");
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");
    private static final Pattern JDBC_CRED_PATTERN = Pattern.compile("(?i)(password|pwd)=[^;&\\s]+");

    private final DevLogService devLogService;
    private final ConcurrentLinkedDeque<LogEntry> ringBuffer = new ConcurrentLinkedDeque<>();
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    public DiagnosticLogService(DevLogService devLogService) {
        this.devLogService = devLogService;
    }

    @PostConstruct
    public void init() {
        attachLiveLogAppender();
    }

    private void attachLiveLogAppender() {
        try {
            if (LoggerFactory.getILoggerFactory() instanceof LoggerContext) {
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                ch.qos.logback.classic.Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

                if (rootLogger.getAppender(APPENDER_NAME) == null) {
                    AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
                        @Override
                        protected void append(ILoggingEvent eventObject) {
                            try {
                                long seq = sequenceCounter.incrementAndGet();
                                String formatted = formatEvent(eventObject);
                                LogEntry entry = new LogEntry(
                                        seq,
                                        eventObject.getTimeStamp(),
                                        eventObject.getLevel() != null ? eventObject.getLevel().toString() : "INFO",
                                        eventObject.getLoggerName(),
                                        eventObject.getFormattedMessage(),
                                        formatted
                                );
                                ringBuffer.addLast(entry);
                                while (ringBuffer.size() > MAX_BUFFER_CAPACITY) {
                                    ringBuffer.pollFirst();
                                }
                            } catch (Exception ignored) {}
                        }
                    };
                    appender.setName(APPENDER_NAME);
                    appender.setContext(context);
                    appender.start();
                    rootLogger.addAppender(appender);
                    log.info("Diagnostic continuous log stream appender attached successfully.");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to attach diagnostic live log appender: {}", e.getMessage());
        }
    }

    private String formatEvent(ILoggingEvent event) {
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault());
        String ts = ldt.format(TIMESTAMP_FORMAT);
        String thread = event.getThreadName();
        String level = event.getLevel() != null ? String.format("%-5s", event.getLevel().toString()) : "INFO ";
        String logger = event.getLoggerName();
        // Shorten long logger names
        if (logger != null && logger.length() > 36) {
            logger = logger.substring(logger.length() - 36);
        }
        return ts + " [" + thread + "] " + level + " " + logger + " - " + event.getFormattedMessage();
    }

    /**
     * Retrieves incremental log stream starting from cursor sinceSeq.
     * If sinceSeq <= 0, returns the initial tail (up to maxLines).
     * If sinceSeq > 0, returns only newly arrived lines since that sequence.
     * If the client cursor falls behind or has an invalid future sequence, returns resyncRequired: true.
     */
    public Map<String, Object> getLogsSince(long sinceSeq, int maxLines, boolean sanitize) {
        int limit = Math.min(Math.max(maxLines, 1), 500);
        List<String> lines = new ArrayList<>();
        long nextSeq = sinceSeq;

        LogEntry firstEntry = ringBuffer.peekFirst();
        LogEntry lastEntry = ringBuffer.peekLast();
        long oldestSeq = firstEntry != null ? firstEntry.getSequence() : 0L;
        long latestSeq = lastEntry != null ? lastEntry.getSequence() : sequenceCounter.get();

        boolean resyncRequired = false;
        if (sinceSeq > 0) {
            if (latestSeq > 0 && sinceSeq > latestSeq) {
                // Client sequence is from the future or previous server run
                resyncRequired = true;
            } else if (oldestSeq > 0 && sinceSeq < oldestSeq - 1) {
                // Client sequence fell behind the bounded ring buffer
                resyncRequired = true;
            }
        }

        if (sinceSeq <= 0 || resyncRequired) {
            // First load or resync: return the latest tail
            List<LogEntry> snapshot = new ArrayList<>(ringBuffer);
            int total = snapshot.size();
            int start = Math.max(0, total - limit);
            for (int i = start; i < total; i++) {
                LogEntry entry = snapshot.get(i);
                lines.add(sanitize ? sanitizeLine(entry.getFormatted()) : entry.getFormatted());
                if (entry.getSequence() > nextSeq) {
                    nextSeq = entry.getSequence();
                }
            }
            // Fallback to devLogService file if ring buffer was empty upon startup
            if (lines.isEmpty()) {
                List<String> fileLines = getRecentLogs(limit);
                for (String fl : fileLines) {
                    lines.add(sanitize ? sanitizeLine(fl) : fl);
                }
                nextSeq = sequenceCounter.get();
            }
        } else {
            // Incremental poll: collect only lines with sequence > sinceSeq
            for (LogEntry entry : ringBuffer) {
                if (entry.getSequence() > sinceSeq) {
                    lines.add(sanitize ? sanitizeLine(entry.getFormatted()) : entry.getFormatted());
                    if (entry.getSequence() > nextSeq) {
                        nextSeq = entry.getSequence();
                    }
                    if (lines.size() >= limit) {
                        break;
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("lines", lines);
        result.put("nextSeq", nextSeq);
        result.put("hasNew", !lines.isEmpty());
        result.put("totalBuffered", ringBuffer.size());
        result.put("resyncRequired", resyncRequired);
        result.put("earliestSeq", oldestSeq);
        result.put("latestSeq", latestSeq);
        return result;
    }

    /**
     * Snapshots the entire in-memory ring buffer (up to MAX_BUFFER_CAPACITY entries)
     * formatted as a single multiline string for on-demand download.
     * Does NOT alter sequenceCounter, ringBuffer, or client cursors.
     */
    public String exportAllBufferedLogs(boolean sanitize) {
        List<LogEntry> snapshot = new ArrayList<>(ringBuffer);
        StringBuilder sb = new StringBuilder(snapshot.size() * 160);
        for (int i = 0; i < snapshot.size(); i++) {
            LogEntry entry = snapshot.get(i);
            String formatted = entry.getFormatted();
            if (sanitize) {
                formatted = sanitizeLine(formatted);
            }
            sb.append(formatted);
            if (i < snapshot.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String sanitizeLine(String line) {
        if (line == null || line.isBlank()) return "";
        String sanitized = MASTER_KEY_PATTERN.matcher(line).replaceAll("***REDACTED_MASTER_KEY***");
        sanitized = SENSITIVE_KEY_PATTERN.matcher(sanitized).replaceAll("$1$2$3: \"***REDACTED***\"");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("$1***REDACTED***");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("***REDACTED_JWT***");
        sanitized = SESSION_ID_PATTERN.matcher(sanitized).replaceAll("$1***REDACTED_SESSION***");
        sanitized = CARD_NUMBER_PATTERN.matcher(sanitized).replaceAll("****-****-****-****");
        sanitized = JDBC_CRED_PATTERN.matcher(sanitized).replaceAll("$1=***REDACTED***");
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRecentLogs(int maxLines) {
        Map<String, Object> status = devLogService.getStatus();
        Object linesObj = status.get("recentLines");
        if (linesObj instanceof List) {
            List<String> lines = (List<String>) linesObj;
            if (lines.size() > maxLines) {
                return lines.subList(lines.size() - maxLines, lines.size());
            }
            return lines;
        }
        return List.of();
    }

    public File getLogFile() {
        return devLogService.getLogFilePath().toFile();
    }

    public Map<String, Object> setVerboseLogging(boolean enable) {
        return devLogService.setEnabled(enable);
    }

    public Map<String, Object> getStatus() {
        return devLogService.getStatus();
    }

    public static class LogEntry {
        private final long sequence;
        private final long timestamp;
        private final String level;
        private final String loggerName;
        private final String message;
        private final String formatted;

        public LogEntry(long sequence, long timestamp, String level, String loggerName, String message, String formatted) {
            this.sequence = sequence;
            this.timestamp = timestamp;
            this.level = level;
            this.loggerName = loggerName;
            this.message = message;
            this.formatted = formatted;
        }

        public long getSequence() { return sequence; }
        public long getTimestamp() { return timestamp; }
        public String getLevel() { return level; }
        public String getLoggerName() { return loggerName; }
        public String getMessage() { return message; }
        public String getFormatted() { return formatted; }
    }
}
