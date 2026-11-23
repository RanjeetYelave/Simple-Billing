package com.billing.simple.billsoft.demo;

import com.billing.simple.billsoft.security.DatabaseSafetyGuard;
import com.billing.simple.billsoft.util.DataDirectoryResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RoutingDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(RoutingDataSourceConfig.class);

    @Value("${spring.datasource.url}")
    private String normalUrl;

    @Value("${spring.datasource.driverClassName:org.h2.Driver}")
    private String driverClassName;

    @Value("${spring.datasource.username:sa}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("Initializing RoutingDataSourceConfig with normal URL: {}", normalUrl);

        // 1. Configure Primary / Normal DataSource
        HikariConfig normalConfig = new HikariConfig();
        normalConfig.setJdbcUrl(normalUrl);
        normalConfig.setDriverClassName(driverClassName);
        normalConfig.setUsername(username);
        normalConfig.setPassword(password);
        normalConfig.setPoolName("HikariPool-Normal");
        normalConfig.setMaximumPoolSize(10);
        normalConfig.setMinimumIdle(2);
        normalConfig.setConnectionTimeout(30000);
        HikariDataSource normalDs = new HikariDataSource(normalConfig);

        // 2. Configure Dedicated Demo DataSource
        String demoUrl;
        if (DatabaseSafetyGuard.isTestEnvironment()) {
            if (normalUrl.startsWith("jdbc:h2:mem:")) {
                demoUrl = "jdbc:h2:mem:demodb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
            } else {
                File demoDir = DataDirectoryResolver.resolveDemoDirectory();
                demoUrl = "jdbc:h2:file:" + new File(demoDir, "demo_database").getAbsolutePath() + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
            }
        } else {
            File demoDir = DataDirectoryResolver.resolveDemoDirectory();
            demoUrl = "jdbc:h2:file:" + new File(demoDir, "demo_database").getAbsolutePath() + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        }
        log.info("Configured isolated Demo DataSource URL: {}", demoUrl);

        HikariConfig demoConfig = new HikariConfig();
        demoConfig.setJdbcUrl(demoUrl);
        demoConfig.setDriverClassName(driverClassName);
        demoConfig.setUsername(username);
        demoConfig.setPassword(password);
        demoConfig.setPoolName("HikariPool-Demo");
        demoConfig.setMaximumPoolSize(10);
        demoConfig.setMinimumIdle(2);
        demoConfig.setConnectionTimeout(30000);
        HikariDataSource demoDs = new HikariDataSource(demoConfig);

        // 3. Assemble DynamicRoutingDataSource
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(EnvironmentMode.NORMAL, normalDs);
        targetDataSources.put(EnvironmentMode.DEMO, demoDs);

        DynamicRoutingDataSource routingDs = new DynamicRoutingDataSource(normalDs, demoDs);
        routingDs.setTargetDataSources(targetDataSources);
        routingDs.setDefaultTargetDataSource(normalDs);
        routingDs.afterPropertiesSet();

        return routingDs;
    }
}
