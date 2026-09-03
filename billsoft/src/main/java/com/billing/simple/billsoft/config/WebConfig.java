package com.billing.simple.billsoft.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

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
                );
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Serve index.html for the root path and any non-API, non-resource paths
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}