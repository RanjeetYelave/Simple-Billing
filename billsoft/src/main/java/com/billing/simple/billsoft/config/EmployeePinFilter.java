package com.billing.simple.billsoft.config;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(2)
public class EmployeePinFilter implements Filter {

    private final AppConfigRepository appConfigRepo;

    public EmployeePinFilter(AppConfigRepository appConfigRepo) {
        this.appConfigRepo = appConfigRepo;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI();

        // Only filter /api/employees/** routes
        if (!path.startsWith("/api/employees")
                || path.equals("/api/employees/verify-pin")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        String configuredPin = appConfigRepo.findById("EMPLOYEE_MODULE_PIN")
                .map(AppConfig::getConfigValue)
                .orElse("0000");

        String providedPin = request.getHeader("X-Employee-Pin");
        if (providedPin == null || providedPin.isBlank()) {
            providedPin = request.getParameter("pin");
        }

        if (providedPin != null && providedPin.equals(configuredPin)) {
            chain.doFilter(req, res);
            return;
        }

        // Return 403 Forbidden
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Forbidden: Invalid or missing Employee PIN\",\"status\":403}");
    }
}
