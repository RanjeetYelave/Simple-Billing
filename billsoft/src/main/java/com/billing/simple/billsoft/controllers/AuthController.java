package com.billing.simple.billsoft.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.service.AuthService;
import com.billing.simple.billsoft.service.AuthService.DeveloperResetRequest;
import com.billing.simple.billsoft.service.AuthService.LoginRequest;
import com.billing.simple.billsoft.service.AuthService.LoginResult;
import com.billing.simple.billsoft.service.AuthService.RegisterRequest;
import com.billing.simple.billsoft.service.AuthService.RegisterResult;
import com.billing.simple.billsoft.service.AuthService.SimpleResult;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    // ---------------- REGISTER / CREATE ACCOUNT ----------------

    /**
     * Create or update account for the (single) firm.
     * Body: { "loginId": "...", "password": "..." }
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResult> register(@RequestBody RegisterRequest req) {
        RegisterResult result = auth.register(req);
        if (!result.success) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // ---------------- LOGIN ----------------

    /**
     * Login.
     * Body: { "loginId": "...", "password": "...", "activationKey": "..." (optional) }
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@RequestBody LoginRequest req) {
        LoginResult result = auth.login(req);
        if (!result.success) {
            return ResponseEntity.status(400).body(result);
        }
        return ResponseEntity.ok(result);
    }

    // ---------------- FORGOT PASSWORD / DEVELOPER RESET ----------------

    /**
     * Reset password with developer key.
     * Body: { "firmId": 1, "developerKey": "...", "newPassword": "..." }
     *
     * Front-end MUST:
     *  - Ask for firmId
     *  - Show big red warning: "DO NOT ENTER RANDOM KEYS – YOU MAY LOSE DATA"
     *  - Mention that this is a paid service and only for support use.
     */
    @PostMapping("/forgot-password/developer-reset")
    public ResponseEntity<SimpleResult> developerReset(@RequestBody DeveloperResetRequest req) {
        SimpleResult result = auth.resetPasswordWithDeveloperKey(req);
        if (!result.success) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
