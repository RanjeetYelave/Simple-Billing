package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.scheduler.CheckType;
import com.billing.simple.billsoft.service.scheduler.GitCheckExecutionResult;
import com.billing.simple.billsoft.service.scheduler.GitCheckSchedulerService;
import com.billing.simple.billsoft.service.scheduler.GitCheckState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for monitoring and interacting with the centralized Git-check scheduler.
 */
@RestController
@RequestMapping("/api/scheduler/git-checks")
@CrossOrigin
public class GitSchedulerController {

    private final GitCheckSchedulerService schedulerService;

    @Autowired
    public GitSchedulerController(GitCheckSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        Map<String, Object> resp = new HashMap<>();
        Instant now = Instant.now(schedulerService.getClock());
        resp.put("timestamp", now);

        Map<String, Object> checksMap = new HashMap<>();
        for (Map.Entry<CheckType, GitCheckState> entry : schedulerService.getAllStates().entrySet()) {
            CheckType type = entry.getKey();
            GitCheckState state = entry.getValue();

            Map<String, Object> stateInfo = new HashMap<>();
            stateInfo.put("type", type.name());
            stateInfo.put("lastAttemptAt", state.getLastAttemptAt());
            stateInfo.put("lastSuccessAt", state.getLastSuccessAt());
            stateInfo.put("nextScheduledAt", state.getNextScheduledAt());
            stateInfo.put("etag", state.getEtag());
            stateInfo.put("lastResult", state.getLastResult());
            stateInfo.put("consecutiveFailures", state.getConsecutiveFailures());
            stateInfo.put("lastErrorMessage", state.getLastErrorMessage());
            stateInfo.put("isDue", state.isDue(now));
            stateInfo.put("intervalHours", schedulerService.calculateInterval(type).toHours());

            checksMap.put(type.name(), stateInfo);
        }

        resp.put("checks", checksMap);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refresh/{type}")
    public ResponseEntity<Map<String, Object>> refreshCheck(@PathVariable("type") String typeStr,
                                                             @RequestParam(value = "force", defaultValue = "true") boolean force) {
        Map<String, Object> resp = new HashMap<>();
        try {
            CheckType type = CheckType.valueOf(typeStr.toUpperCase().trim());
            GitCheckExecutionResult result = schedulerService.triggerCheck(type, force);
            resp.put("success", result.isSuccess());
            resp.put("resultStatus", result.getResultStatus());
            resp.put("etag", result.getEtag());
            resp.put("notModified", result.isNotModified());
            resp.put("errorMessage", result.getErrorMessage());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            resp.put("success", false);
            resp.put("errorMessage", "Unknown check type: " + typeStr);
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/refresh-all")
    public ResponseEntity<Map<String, Object>> refreshAll(@RequestParam(value = "force", defaultValue = "false") boolean force) {
        Map<String, Object> resp = new HashMap<>();
        Map<String, Object> results = new HashMap<>();
        for (CheckType type : CheckType.values()) {
            GitCheckExecutionResult result = schedulerService.triggerCheck(type, force);
            results.put(type.name(), Map.of(
                    "success", result.isSuccess(),
                    "resultStatus", result.getResultStatus(),
                    "notModified", result.isNotModified()
            ));
        }
        resp.put("success", true);
        resp.put("results", results);
        return ResponseEntity.ok(resp);
    }
}

