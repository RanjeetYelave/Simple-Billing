package com.billing.simple.billsoft.security;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Server-side security interceptor for Employee Module APIs (AUDIT-019).
 * Enforces firm-scoped employee module PIN validation and tenant context.
 */
@Component
public class EmployeeSecurityInterceptor implements HandlerInterceptor {

    private final AppConfigRepository appConfigRepo;
    private final com.billing.simple.billsoft.repo.EmployeeRepository employeeRepo;

    private static final java.util.regex.Pattern EMPLOYEE_PATH_PATTERN =
            java.util.regex.Pattern.compile("^/api/employees/(\\d+)(?:/.*)?$");

    public EmployeeSecurityInterceptor(AppConfigRepository appConfigRepo,
                                      com.billing.simple.billsoft.repo.EmployeeRepository employeeRepo) {
        this.appConfigRepo = appConfigRepo;
        this.employeeRepo = employeeRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();

        // 1. Establish and validate tenant context (AUDIT-038: tenant context must be established for all employee endpoints)
        Long firmId = TenantContext.getCurrentFirmId();
        if (firmId == null) {
            String firmHeader = request.getHeader("X-Firm-Id");
            String firmParam = request.getParameter("firmId");
            String firmVal = (firmHeader != null && !firmHeader.isBlank()) ? firmHeader : firmParam;
            if (firmVal != null && !firmVal.isBlank()) {
                try {
                    firmId = Long.parseLong(firmVal.trim());
                    if (firmId > 0) {
                        TenantContext.setCurrentFirmId(firmId);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        java.util.regex.Matcher matcher = EMPLOYEE_PATH_PATTERN.matcher(uri);
        if (matcher.find()) {
            try {
                Long empId = Long.parseLong(matcher.group(1));
                Long empFirmId = employeeRepo.findById(empId).map(com.billing.simple.billsoft.entities.Employee::getFirmId).orElse(null);
                if (empFirmId != null) {
                    if (firmId != null && !firmId.equals(empFirmId)) {
                        throw new TenantSecurityException("Cross-firm employee access prohibited");
                    }
                    firmId = empFirmId;
                    TenantContext.setCurrentFirmId(firmId);
                }
            } catch (NumberFormatException ignored) {}
        }

        // Allow PIN verification and PIN changing endpoints to bypass the X-Employee-Pin header check
        if (uri.endsWith("/verify-pin") || uri.endsWith("/change-pin")) {
            return true;
        }

        // 2. Validate X-Employee-Pin header (strictly required)
        String clientPin = request.getHeader("X-Employee-Pin");
        if (clientPin == null || clientPin.trim().isEmpty()) {
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Missing Employee Module PIN header (X-Employee-Pin)");
            return false;
        }

        if (firmId == null) {
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Active firm context is required for employee operations");
            return false;
        }

        // 3. Compare against current firm-scoped PIN configuration (default "0000")
        String configKey = "EMPLOYEE_MODULE_PIN_" + firmId;
        String configuredPin = appConfigRepo.findById(configKey)
                .map(AppConfig::getConfigValue)
                .orElse("0000");

        if (!configuredPin.equals(clientPin.trim())) {
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Invalid Employee Module PIN");
            return false;
        }

        return true;
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
