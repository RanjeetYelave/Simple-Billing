package com.billing.simple.billsoft.controllers;

import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.service.AuthService;
import com.billing.simple.billsoft.service.AuthService.DeveloperResetRequest;
import com.billing.simple.billsoft.service.AuthService.DevResetValidationRequest;
import com.billing.simple.billsoft.service.AuthService.DevResetValidationResult;
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

    // -------- REGISTER --------
    @PostMapping("/register")
    public RegisterResult register(@RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    // -------- LOGIN --------
    @PostMapping("/login")
    public LoginResult login(@RequestBody LoginRequest req) {
        return auth.login(req);
    }

    // -------- RESET VALIDATION (Step 1) --------
    @PostMapping("/forgot-password/validate")
    public DevResetValidationResult validateReset(@RequestBody DevResetValidationRequest req) {
        return auth.validateResetDev(req);
    }

    // -------- DO RESET (Step 2) --------
    @PostMapping("/forgot-password/developer-reset")
    public SimpleResult developerReset(@RequestBody DeveloperResetRequest req) {
        return auth.resetPasswordDev(req);
    }
}
