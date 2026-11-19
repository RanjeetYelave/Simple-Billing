package com.billing.simple.billsoft.service.scheduler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Root container persisted to git_scheduler_state.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitSchedulerState {

    private int version = 1;
    private Instant lastSavedAt;
    private Map<CheckType, GitCheckState> checks = new EnumMap<>(CheckType.class);

    public GitSchedulerState() {
        for (CheckType type : CheckType.values()) {
            checks.put(type, new GitCheckState(type));
        }
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getLastSavedAt() {
        return lastSavedAt;
    }

    public void setLastSavedAt(Instant lastSavedAt) {
        this.lastSavedAt = lastSavedAt;
    }

    public Map<CheckType, GitCheckState> getChecks() {
        return checks;
    }

    public void setChecks(Map<CheckType, GitCheckState> checks) {
        this.checks = checks != null ? checks : new EnumMap<>(CheckType.class);
    }

    public GitCheckState getCheckState(CheckType type) {
        return checks.computeIfAbsent(type, GitCheckState::new);
    }
}

