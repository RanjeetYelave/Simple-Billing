package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.SystemStat;
import com.billing.simple.billsoft.repositories.SystemStatRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SystemMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsService.class);

    private final SystemStatRepository systemStatRepo;
    private final long serverStartTime = System.currentTimeMillis();

    private final AtomicLong sessionRequests = new AtomicLong(0);
    private long initialLifetimeRequests = 0;
    private long initialLifetimeUptimeSeconds = 0;
    private long serverStartsCount = 1;
    private long firstStartedAt = System.currentTimeMillis();
    private long lastStartedAt = System.currentTimeMillis();

    public SystemMetricsService(SystemStatRepository systemStatRepo) {
        this.systemStatRepo = systemStatRepo;
    }

    @PostConstruct
    @Transactional
    public void init() {
        try {
            SystemStat stat = systemStatRepo.findById(1L).orElseGet(() -> {
                SystemStat newStat = new SystemStat(1L);
                newStat.setFirstStartedAt(System.currentTimeMillis());
                newStat.setServerStartsCount(0);
                return newStat;
            });

            if (stat.getFirstStartedAt() == 0) {
                stat.setFirstStartedAt(System.currentTimeMillis());
            }

            stat.setServerStartsCount(stat.getServerStartsCount() + 1);
            stat.setLastStartedAt(System.currentTimeMillis());
            stat.setUpdatedAt(System.currentTimeMillis());

            SystemStat saved = systemStatRepo.save(stat);

            this.initialLifetimeRequests = saved.getLifetimeRequests();
            this.initialLifetimeUptimeSeconds = saved.getLifetimeUptimeSeconds();
            this.serverStartsCount = saved.getServerStartsCount();
            this.firstStartedAt = saved.getFirstStartedAt();
            this.lastStartedAt = saved.getLastStartedAt();

            log.info("SystemMetricsService initialized: {} lifetime reqs, {}s lifetime uptime, start #{}",
                    initialLifetimeRequests, initialLifetimeUptimeSeconds, serverStartsCount);
        } catch (Exception e) {
            log.warn("Could not load initial SystemStat from database: {}", e.getMessage());
        }
    }

    public void recordRequest() {
        sessionRequests.incrementAndGet();
    }

    public long getSessionRequests() {
        return sessionRequests.get();
    }

    public long getLifetimeRequests() {
        return initialLifetimeRequests + sessionRequests.get();
    }

    public long getSessionUptimeSeconds() {
        return (System.currentTimeMillis() - serverStartTime) / 1000;
    }

    public long getLifetimeUptimeSeconds() {
        return initialLifetimeUptimeSeconds + getSessionUptimeSeconds();
    }

    @Scheduled(fixedRate = 15000)
    @Transactional
    public void flushMetrics() {
        try {
            SystemStat stat = systemStatRepo.findById(1L).orElseGet(() -> new SystemStat(1L));
            stat.setLifetimeRequests(getLifetimeRequests());
            stat.setLifetimeUptimeSeconds(getLifetimeUptimeSeconds());
            stat.setServerStartsCount(this.serverStartsCount);
            stat.setFirstStartedAt(this.firstStartedAt);
            stat.setLastStartedAt(this.lastStartedAt);
            stat.setUpdatedAt(System.currentTimeMillis());
            systemStatRepo.save(stat);
        } catch (Exception e) {
            log.debug("Error flushing system metrics to database: {}", e.getMessage());
        }
    }

    @PreDestroy
    @Transactional
    public void onShutdown() {
        log.info("Flushing final system metrics before shutdown...");
        flushMetrics();
    }

    public Map<String, Object> getMetricsSnapshot() {
        Runtime runtime = Runtime.getRuntime();

        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long maxMem = runtime.maxMemory();
        long usedMem = totalMem - freeMem;

        double usedMb = usedMem / (1024.0 * 1024.0);
        double totalMb = totalMem / (1024.0 * 1024.0);
        double maxMb = maxMem / (1024.0 * 1024.0);
        double ramUsedPct = maxMem > 0 ? (usedMem * 100.0) / maxMem : 0.0;

        File rootDir = new File(".");
        long usableDisk = rootDir.getUsableSpace();
        long totalDisk = rootDir.getTotalSpace();
        double freeDiskGb = usableDisk / (1024.0 * 1024.0 * 1024.0);
        double totalDiskGb = totalDisk / (1024.0 * 1024.0 * 1024.0);
        double diskUsedPct = totalDisk > 0 ? ((totalDisk - usableDisk) * 100.0) / totalDisk : 0.0;

        long sessionUptime = getSessionUptimeSeconds();
        long lifetimeUptime = getLifetimeUptimeSeconds();
        long sessionReqs = getSessionRequests();
        long lifetimeReqs = getLifetimeRequests();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("status", "UP");
        // Session Metrics
        metrics.put("uptimeSeconds", sessionUptime);
        metrics.put("sessionUptimeSeconds", sessionUptime);
        metrics.put("sessionRequestsCount", sessionReqs);
        metrics.put("ramUsedMb", round(usedMb, 1));
        metrics.put("ramTotalMb", round(totalMb, 1));
        metrics.put("ramMaxMb", round(maxMb, 1));
        metrics.put("ramUsedPercent", round(ramUsedPct, 1));
        metrics.put("diskFreeGb", round(freeDiskGb, 1));
        metrics.put("diskTotalGb", round(totalDiskGb, 1));
        metrics.put("diskUsedPercent", round(diskUsedPct, 1));
        metrics.put("activeThreads", Thread.activeCount());
        metrics.put("availableProcessors", runtime.availableProcessors());
        metrics.put("javaVersion", System.getProperty("java.version", "Unknown"));
        metrics.put("osName", System.getProperty("os.name", "Unknown"));
        metrics.put("timestamp", System.currentTimeMillis());

        // Lifetime Metrics
        metrics.put("lifetimeRequestsCount", lifetimeReqs);
        metrics.put("lifetimeUptimeSeconds", lifetimeUptime);
        metrics.put("serverStartsCount", this.serverStartsCount);
        metrics.put("firstStartedAt", this.firstStartedAt);
        metrics.put("lastStartedAt", this.lastStartedAt);

        return metrics;
    }

    private double round(double val, int decimals) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return 0.0;
        return BigDecimal.valueOf(val).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }
}
