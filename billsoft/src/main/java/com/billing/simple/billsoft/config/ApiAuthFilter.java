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
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(req, res);
    }
}
