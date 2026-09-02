package com.billing.simple.billsoft.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_stats")
public class SystemStat {

    @Id
    private Long id = 1L;

    @Column(name = "lifetime_requests")
    private long lifetimeRequests;

    @Column(name = "lifetime_uptime_seconds")
    private long lifetimeUptimeSeconds;

    @Column(name = "server_starts_count")
    private long serverStartsCount;

    @Column(name = "first_started_at")
    private long firstStartedAt;

    @Column(name = "last_started_at")
    private long lastStartedAt;

    @Column(name = "updated_at")
    private long updatedAt;

    public SystemStat() {
    }

    public SystemStat(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getLifetimeRequests() {
        return lifetimeRequests;
    }

    public void setLifetimeRequests(long lifetimeRequests) {
        this.lifetimeRequests = lifetimeRequests;
    }

    public long getLifetimeUptimeSeconds() {
        return lifetimeUptimeSeconds;
    }

    public void setLifetimeUptimeSeconds(long lifetimeUptimeSeconds) {
        this.lifetimeUptimeSeconds = lifetimeUptimeSeconds;
    }

    public long getServerStartsCount() {
        return serverStartsCount;
    }

    public void setServerStartsCount(long serverStartsCount) {
        this.serverStartsCount = serverStartsCount;
    }

    public long getFirstStartedAt() {
        return firstStartedAt;
    }

    public void setFirstStartedAt(long firstStartedAt) {
        this.firstStartedAt = firstStartedAt;
    }

    public long getLastStartedAt() {
        return lastStartedAt;
    }

    public void setLastStartedAt(long lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
