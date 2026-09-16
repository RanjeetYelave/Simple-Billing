package com.billing.simple.billsoft.dtos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured diagnostic response for API health test suite.
 */
public class ApiDiagnosticsResponse implements Serializable {

    private String overallStatus; // HEALTHY, DEGRADED, CRITICAL
    private int totalEndpoints;
    private int passedEndpoints;
    private int failedEndpoints;
    private double passRatePercent;
    private long totalDurationMs;
    private double averageLatencyMs;
    private long timestamp;
    private List<EndpointResult> results = new ArrayList<>();
    private Map<String, CategorySummary> categorySummaries = new HashMap<>();

    public ApiDiagnosticsResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public int getTotalEndpoints() {
        return totalEndpoints;
    }

    public void setTotalEndpoints(int totalEndpoints) {
        this.totalEndpoints = totalEndpoints;
    }

    public int getPassedEndpoints() {
        return passedEndpoints;
    }

    public void setPassedEndpoints(int passedEndpoints) {
        this.passedEndpoints = passedEndpoints;
    }

    public int getFailedEndpoints() {
        return failedEndpoints;
    }

    public void setFailedEndpoints(int failedEndpoints) {
        this.failedEndpoints = failedEndpoints;
    }

    public double getPassRatePercent() {
        return passRatePercent;
    }

    public void setPassRatePercent(double passRatePercent) {
        this.passRatePercent = passRatePercent;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public void setAverageLatencyMs(double averageLatencyMs) {
        this.averageLatencyMs = averageLatencyMs;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public List<EndpointResult> getResults() {
        return results;
    }

    public void setResults(List<EndpointResult> results) {
        this.results = results;
    }

    public Map<String, CategorySummary> getCategorySummaries() {
        return categorySummaries;
    }

    public void setCategorySummaries(Map<String, CategorySummary> categorySummaries) {
        this.categorySummaries = categorySummaries;
    }

    public static class EndpointResult implements Serializable {
        private String category;
        private String name;
        private String method;
        private String endpoint;
        private int statusCode;
        private boolean success;
        private long latencyMs;
        private String message;
        private String responseSummary;

        public EndpointResult() {}

        public EndpointResult(String category, String name, String method, String endpoint,
                              int statusCode, boolean success, long latencyMs,
                              String message, String responseSummary) {
            this.category = category;
            this.name = name;
            this.method = method;
            this.endpoint = endpoint;
            this.statusCode = statusCode;
            this.success = success;
            this.latencyMs = latencyMs;
            this.message = message;
            this.responseSummary = responseSummary;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        public void setLatencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getResponseSummary() {
            return responseSummary;
        }

        public void setResponseSummary(String responseSummary) {
            this.responseSummary = responseSummary;
        }
    }

    public static class CategorySummary implements Serializable {
        private String category;
        private int total;
        private int passed;
        private int failed;
        private double avgLatencyMs;

        public CategorySummary() {}

        public CategorySummary(String category, int total, int passed, int failed, double avgLatencyMs) {
            this.category = category;
            this.total = total;
            this.passed = passed;
            this.failed = failed;
            this.avgLatencyMs = avgLatencyMs;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getPassed() {
            return passed;
        }

        public void setPassed(int passed) {
            this.passed = passed;
        }

        public int getFailed() {
            return failed;
        }

        public void setFailed(int failed) {
            this.failed = failed;
        }

        public double getAvgLatencyMs() {
            return avgLatencyMs;
        }

        public void setAvgLatencyMs(double avgLatencyMs) {
            this.avgLatencyMs = avgLatencyMs;
        }
    }
}
