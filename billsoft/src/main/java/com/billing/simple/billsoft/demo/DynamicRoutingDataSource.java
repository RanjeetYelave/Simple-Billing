package com.billing.simple.billsoft.demo;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;

/**
 * Dynamic routing DataSource that seamlessly routes database operations
 * between the primary production/normal database and the isolated demo database.
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    private final HikariDataSource normalDataSource;
    private final HikariDataSource demoDataSource;

    public DynamicRoutingDataSource(HikariDataSource normalDataSource, HikariDataSource demoDataSource) {
        this.normalDataSource = normalDataSource;
        this.demoDataSource = demoDataSource;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return EnvironmentModeContext.getCurrentMode();
    }

    public HikariDataSource getNormalDataSource() {
        return normalDataSource;
    }

    public HikariDataSource getDemoDataSource() {
        return demoDataSource;
    }

    public DataSource getActiveDataSource() {
        EnvironmentMode mode = EnvironmentModeContext.getCurrentMode();
        return mode == EnvironmentMode.DEMO ? demoDataSource : normalDataSource;
    }

    /**
     * Used by reflection in DatabaseSafetyGuard.getJdbcUrl()
     */
    public String getJdbcUrl() {
        return getUrl();
    }

    /**
     * Used by reflection in DatabaseSafetyGuard.getJdbcUrl()
     */
    public String getUrl() {
        EnvironmentMode mode = EnvironmentModeContext.getCurrentMode();
        if (mode == EnvironmentMode.DEMO) {
            return demoDataSource != null ? demoDataSource.getJdbcUrl() : null;
        }
        return normalDataSource != null ? normalDataSource.getJdbcUrl() : null;
    }
}
