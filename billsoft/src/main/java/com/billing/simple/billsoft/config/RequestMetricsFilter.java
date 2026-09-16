package com.billing.simple.billsoft.config;

import com.billing.simple.billsoft.service.SystemMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final SystemMetricsService systemMetricsService;

    public RequestMetricsFilter(SystemMetricsService systemMetricsService) {
        this.systemMetricsService = systemMetricsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api")) {
            systemMetricsService.recordRequest();
        }
        filterChain.doFilter(request, response);
    }
}
