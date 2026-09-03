package com.billing.simple.billsoft.config;

import com.billing.simple.billsoft.controllers.AuthController;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class ApiAuthFilter implements Filter {

    private final AppConfigRepository appConfigRepo;
    private final AuthController authController;

    public ApiAuthFilter(AppConfigRepository appConfigRepo, AuthController authController) {
        this.appConfigRepo = appConfigRepo;
        this.authController = authController;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI();

        // 1. Allow static resources, error pages, auth APIs, health & metrics APIs, and CORS preflight OPTIONS
        if (!path.startsWith("/api/")
                || path.startsWith("/api/auth/")
                || path.startsWith("/api/health")
                || path.startsWith("/api/stats")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        // 2. Check if global authentication is enabled
        boolean authEnabled = appConfigRepo.findById("auth_enabled")
                .map(AppConfig::getConfigValue)
                .map(Boolean::parseBoolean)
                .orElse(false);

        if (!authEnabled) {
            chain.doFilter(req, res);
            return;
        }

        // 3. Verify session token from headers
        String token = request.getHeader("X-Auth-Token");
        if (token == null || token.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            }
        }

        if (token != null && authController.isValidSessionToken(token)) {
            chain.doFilter(req, res);
            return;
        }

        // 4. Reject unauthenticated API request
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized: Valid session token required\",\"status\":401}");
    }
}
