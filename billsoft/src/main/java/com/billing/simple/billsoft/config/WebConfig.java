package com.billing.simple.billsoft.config;

import com.billing.simple.billsoft.security.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final com.billing.simple.billsoft.security.EmployeeSecurityInterceptor employeeSecurityInterceptor;

    public WebConfig(TenantInterceptor tenantInterceptor,
                     com.billing.simple.billsoft.security.EmployeeSecurityInterceptor employeeSecurityInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
        this.employeeSecurityInterceptor = employeeSecurityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/recovery/**");

        registry.addInterceptor(employeeSecurityInterceptor)
                .addPathPatterns("/api/employees/**")
                .excludePathPatterns("/api/employees/verify-pin", "/api/employees/change-pin");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(
                        "classpath:/META-INF/resources/",
                        "classpath:/resources/",
                        "classpath:/static/",
                        "classpath:/public/",
                        "file:src/main/webapp/",
                        "file:billsoft/src/main/webapp/"
                )
                .setCacheControl(org.springframework.http.CacheControl.noCache().mustRevalidate());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Serve index.html for the root path and any non-API, non-resource paths
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}