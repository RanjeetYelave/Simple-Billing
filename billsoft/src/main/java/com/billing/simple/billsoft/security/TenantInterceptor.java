package com.billing.simple.billsoft.security;

import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final FirmDetailsRepository firmDetailsRepo;

    private static final Set<String> GLOBAL_PREFIXES = Set.of(
            "/api/firm",
            "/api/auth",
            "/api/health",
            "/api/system",
            "/api/diagnostics",
            "/api/update",
            "/api/license",
            "/api/backup"
    );

    public TenantInterceptor(FirmDetailsRepository firmDetailsRepo) {
        this.firmDetailsRepo = firmDetailsRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();

        // Static resources or non-api paths
        if (!uri.startsWith("/api/")) {
            return true;
        }

        // Global APIs that do not require tenant scoping
        for (String prefix : GLOBAL_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                extractAndSetFirmIfValid(request);
                return true;
            }
        }

        // Resolve firm from request
        String firmHeader = request.getHeader("X-Firm-Id");
        String firmParam = request.getParameter("firmId");
        String firmVal = (firmHeader != null && !firmHeader.isBlank()) ? firmHeader : firmParam;

        if (firmVal != null && !firmVal.isBlank()) {
            Long firmId;
            try {
                firmId = Long.parseLong(firmVal.trim());
                if (firmId <= 0) throw new NumberFormatException("Negative or zero firmId");
            } catch (NumberFormatException e) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid firm ID specified.");
                return false;
            }

            TenantContext.setCurrentFirmId(firmId);
        }

        return true;
    }

    private void extractAndSetFirmIfValid(HttpServletRequest request) {
        String firmHeader = request.getHeader("X-Firm-Id");
        String firmParam = request.getParameter("firmId");
        String firmVal = (firmHeader != null && !firmHeader.isBlank()) ? firmHeader : firmParam;
        if (firmVal != null && !firmVal.isBlank()) {
            try {
                Long fid = Long.parseLong(firmVal.trim());
                if (fid > 0) {
                    TenantContext.setCurrentFirmId(fid);
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
