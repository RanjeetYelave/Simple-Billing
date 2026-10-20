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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final FirmDetailsRepository firmDetailsRepo;

    private static final Pattern FIRM_PATH_PATTERN = Pattern.compile("^/api/[^/]+/firm/(\\d+)(?:/.*)?$");
    private static final Pattern FIRM_DIRECT_PATH_PATTERN = Pattern.compile("^/api/firm/(\\d+)(?:/.*)?$");
    private static final Pattern INVOICE_MIGRATION_PATTERN = Pattern.compile(
            "^/api/invoices/\\d+/(migration-preview|migrate|revert-migration|migration-history)(?:/.*)?$"
    );

    private static final Set<String> GLOBAL_PREFIXES = Set.of(
            "/api/auth",
            "/api/health",
            "/api/system",
            "/api/diagnostics",
            "/api/recovery",
            "/api/update",
            "/api/license",
            "/api/licensing",
            "/api/announcements",
            "/api/backup",
            "/api/dataprotection",
            "/api/dev",
            "/api/app-config"
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

        // Special tenant-agnostic sub-resource check: /api/omnisearch/qr is public/tenant-agnostic
        if (uri.equals("/api/omnisearch/qr") || uri.startsWith("/api/omnisearch/qr/")) {
            extractAndSetFirmIfValid(request);
            return true;
        }

        // Global firm collection listing or creation (tenant-agnostic)
        if (uri.equals("/api/firm") || uri.equals("/api/firm/")) {
            extractAndSetFirmIfValid(request);
            return true;
        }

        // Global administrative & system APIs that manage their own master/loopback authorization
        for (String prefix : GLOBAL_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                extractAndSetFirmIfValid(request);
                return true;
            }
        }

        // 1. Extract client-supplied firm from header or query param
        String firmHeader = request.getHeader("X-Firm-Id");
        String firmParam = request.getParameter("firmId");
        String firmVal = (firmHeader != null && !firmHeader.isBlank()) ? firmHeader : firmParam;
        Long clientSuppliedFirmId = null;

        if (firmVal != null && !firmVal.isBlank()) {
            try {
                clientSuppliedFirmId = Long.parseLong(firmVal.trim());
                if (clientSuppliedFirmId <= 0) throw new NumberFormatException("Negative or zero firmId");
            } catch (NumberFormatException e) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid firm ID specified.");
                return false;
            }
        }

        // 2. Extract path firm variable if present (/api/{entity}/firm/{firmId} or /api/firm/{firmId})
        Long pathFirmId = null;
        Matcher m = FIRM_PATH_PATTERN.matcher(uri);
        if (!m.matches()) {
            m = FIRM_DIRECT_PATH_PATTERN.matcher(uri);
        }
        if (m.matches()) {
            try {
                pathFirmId = Long.parseLong(m.group(1));
                if (pathFirmId <= 0) throw new NumberFormatException("Negative or zero path firmId");
            } catch (NumberFormatException e) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid firm ID in URL path.");
                return false;
            }
        }

        // 3. Special strict checks for invoice migration operations
        if (INVOICE_MIGRATION_PATTERN.matcher(uri).matches()) {
            if (clientSuppliedFirmId == null && pathFirmId == null) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Active firm context (X-Firm-Id) is required for invoice migration operations.");
                return false;
            }
            Long fid = pathFirmId != null ? pathFirmId : clientSuppliedFirmId;
            if (!firmDetailsRepo.existsById(fid)) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Active firm context (" + fid + ") does not exist.");
                return false;
            }
        }

        // OmniSearch operations (excluding QR)
        if (uri.startsWith("/api/omnisearch") && !uri.equals("/api/omnisearch/qr") && !uri.startsWith("/api/omnisearch/qr/")) {
            if (clientSuppliedFirmId == null && pathFirmId == null) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Active firm context (X-Firm-Id) is required for OmniSearch operations.");
                return false;
            }
        }

        // 4. Validate cross-firm consistency if both path and header/param are provided
        if (pathFirmId != null && clientSuppliedFirmId != null && !clientSuppliedFirmId.equals(pathFirmId)) {
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Cross-firm mismatch between path (" + pathFirmId + ") and header/parameter (" + clientSuppliedFirmId + ").");
            return false;
        }

        Long effectiveFirmId = pathFirmId != null ? pathFirmId : clientSuppliedFirmId;
        if (effectiveFirmId != null) {
            TenantContext.setCurrentFirmId(effectiveFirmId);
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
