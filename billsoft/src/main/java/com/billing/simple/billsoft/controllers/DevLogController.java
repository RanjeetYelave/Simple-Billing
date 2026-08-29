package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.DevLogService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/system/dev-logs")
@CrossOrigin
public class DevLogController {

    private final DevLogService devLogService;

    public DevLogController(DevLogService devLogService) {
        this.devLogService = devLogService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(devLogService.getStatus());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> setEnabled(@RequestBody Map<String, Boolean> payload) {
        boolean enable = payload != null && Boolean.TRUE.equals(payload.get("enabled"));
        return ResponseEntity.ok(devLogService.setEnabled(enable));
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportLogs() {
        Path logFile = devLogService.getLogFilePath();
        byte[] data;
        try {
            if (Files.exists(logFile)) {
                data = Files.readAllBytes(logFile);
            } else {
                data = "No logs recorded yet.".getBytes();
            }
        } catch (Exception e) {
            data = ("Failed to read log file: " + e.getMessage()).getBytes();
        }

        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rupeecrm-developer-debug.log\"")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(data.length)
                .body(resource);
    }
}
