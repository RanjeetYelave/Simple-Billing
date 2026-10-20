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
    private final com.billing.simple.billsoft.repo.FirmDetailsRepository firmDetailsRepo;

    public ApiAuthFilter(AppConfigRepository appConfigRepo, AuthController authController, com.billing.simple.billsoft.repo.FirmDetailsRepository firmDetailsRepo) {
        this.appConfigRepo = appConfigRepo;
        this.authController = authController;
        this.firmDetailsRepo = firmDetailsRepo;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String uri = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !uri.startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }

        // Set strict Referrer-Policy on all API responses to prevent token leakage in Referer headers
        response.setHeader("Referrer-Policy", "no-referrer");

        // Public/exempt API paths
        if (uri.startsWith("/api/auth/") || uri.startsWith("/api/health") || uri.startsWith("/api/recovery/")) {
            chain.doFilter(req, res);
            return;
        }

        // Support scoped single-use progress ticket for /api/system/update-progress
        if (uri.equals("/api/system/update-progress")) {
            String ticket = request.getParameter("ticket");
            if (ticket != null && !ticket.isBlank()) {
                if (com.billing.simple.billsoft.controllers.UpdateController.isValidProgressTicket(ticket)) {
                    request.setAttribute("RUPEE_STREAM_TICKET_VALIDATED", Boolean.TRUE);
                    chain.doFilter(req, res);
                    return;
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized: Invalid or expired streaming ticket\"}");
                    return;
                }
            }
        }

        // Narrow first-run bootstrap exemption: allow firm setup ONLY when database contains zero firms
        if ((uri.equals("/api/firm") || uri.startsWith("/api/firm/")) && firmDetailsRepo.count() == 0) {
            chain.doFilter(req, res);
            return;
        }

        if (authController.isAuthEnabled()) {
            String token = request.getHeader("X-Auth-Token");
            if (token == null || token.isBlank()) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7).trim();
                }
            }
            if (token == null || token.isBlank()) {
                token = request.getParameter("token");
                if (token != null && !token.isBlank()) {
                    response.setHeader("Warning", "299 - \"Query parameter token authentication is deprecated; use Authorization header or streaming ticket\"");
                }
            }

            if (token == null || !authController.isValidSessionToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized: Valid session token required\"}");
                return;
            }
        }

        chain.doFilter(req, res);
    }
}
