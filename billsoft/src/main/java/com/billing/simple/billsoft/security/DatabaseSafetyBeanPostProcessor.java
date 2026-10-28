package com.billing.simple.billsoft.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Spring BeanPostProcessor that intercepts DataSource bean initialization.
 * Enforces that no test context can start or execute JPA operations against a production database.
 */
@Component
public class DatabaseSafetyBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSafetyBeanPostProcessor.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource) {
            log.debug("DatabaseSafetyBeanPostProcessor inspecting DataSource bean '{}'", beanName);
            DatabaseSafetyGuard.assertSafeDataSource(dataSource);
        }
        return bean;
    }
}
