package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.demo.DemoModeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class DemoModeController {

    private final DemoModeService demoModeService;

    @Autowired
    public DemoModeController(DemoModeService demoModeService) {
        this.demoModeService = demoModeService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(demoModeService.getStatus());
    }

    @PostMapping("/enter")
    public ResponseEntity<Map<String, Object>> enterDemoMode(@RequestBody(required = false) Map<String, String> body) {
        String pin = body != null ? body.get("pin") : null;
        try {
            Map<String, Object> result = demoModeService.enterDemoMode(pin);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException iae) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", iae.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", "Failed to enter Demo Mode: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/exit")
    public ResponseEntity<Map<String, Object>> exitDemoMode() {
        try {
            Map<String, Object> result = demoModeService.exitDemoMode();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", "Failed to exit Demo Mode: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetDemoMode() {
        try {
            demoModeService.resetDemoDatabase();
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("message", "Demo database reset to initial predefined state.");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", "Failed to reset Demo Mode: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }
}
