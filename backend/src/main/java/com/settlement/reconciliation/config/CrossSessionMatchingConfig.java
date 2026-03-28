package com.settlement.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "reconciliation.cross-session-matching")
public class CrossSessionMatchingConfig {
    
    // Enable/disable cross-session matching globally
    private boolean enabled = true;
    
    // Matching thresholds
    private MatchingThresholds thresholds = new MatchingThresholds();
    
    // Date range for fuzzy matching
    private int fuzzyDateRangeDays = 7;
    
    // Amount tolerance for fuzzy matching
    private double amountTolerance = 0.01;
    
    // Auto-approve high confidence matches
    private boolean autoApproveHighConfidence = true;
    private double autoApproveThreshold = 95.0;
    
    // Cleanup configuration
    private CleanupConfig cleanup = new CleanupConfig();
    
    // Performance settings
    private PerformanceConfig performance = new PerformanceConfig();
    
    @ConfigurationProperties(prefix = "reconciliation.cross-session-matching.thresholds")
    public static class MatchingThresholds {
        private double exactMatch = 100.0;
        private double fuzzyMatch = 85.0;
        private double dateRangeMatch = 75.0;
        private double minimumConfidence = 70.0;
        
        // Getters and setters
        public double getExactMatch() { return exactMatch; }
        public void setExactMatch(double exactMatch) { this.exactMatch = exactMatch; }
        
        public double getFuzzyMatch() { return fuzzyMatch; }
        public void setFuzzyMatch(double fuzzyMatch) { this.fuzzyMatch = fuzzyMatch; }
        
        public double getDateRangeMatch() { return dateRangeMatch; }
        public void setDateRangeMatch(double dateRangeMatch) { this.dateRangeMatch = dateRangeMatch; }
        
        public double getMinimumConfidence() { return minimumConfidence; }
        public void setMinimumConfidence(double minimumConfidence) { this.minimumConfidence = minimumConfidence; }
    }
    
    @ConfigurationProperties(prefix = "reconciliation.cross-session-matching.cleanup")
    public static class CleanupConfig {
        private boolean enabled = true;
        private int resolvedTransactionRetentionDays = 90;
        private int approvedMatchRetentionDays = 90;
        private boolean runScheduled = true;
        private String cronExpression = "0 0 2 * * ?"; // 2 AM daily
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getResolvedTransactionRetentionDays() { return resolvedTransactionRetentionDays; }
        public void setResolvedTransactionRetentionDays(int resolvedTransactionRetentionDays) { this.resolvedTransactionRetentionDays = resolvedTransactionRetentionDays; }
        
        public int getApprovedMatchRetentionDays() { return approvedMatchRetentionDays; }
        public void setApprovedMatchRetentionDays(int approvedMatchRetentionDays) { this.approvedMatchRetentionDays = approvedMatchRetentionDays; }
        
        public boolean isRunScheduled() { return runScheduled; }
        public void setRunScheduled(boolean runScheduled) { this.runScheduled = runScheduled; }
        
        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    }
    
    @ConfigurationProperties(prefix = "reconciliation.cross-session-matching.performance")
    public static class PerformanceConfig {
        private int batchSize = 1000;
        private int maxConcurrentMatches = 10;
        private long matchingTimeoutMs = 30000; // 30 seconds
        private boolean enableAsyncProcessing = true;
        
        // Getters and setters
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        
        public int getMaxConcurrentMatches() { return maxConcurrentMatches; }
        public void setMaxConcurrentMatches(int maxConcurrentMatches) { this.maxConcurrentMatches = maxConcurrentMatches; }
        
        public long getMatchingTimeoutMs() { return matchingTimeoutMs; }
        public void setMatchingTimeoutMs(long matchingTimeoutMs) { this.matchingTimeoutMs = matchingTimeoutMs; }
        
        public boolean isEnableAsyncProcessing() { return enableAsyncProcessing; }
        public void setEnableAsyncProcessing(boolean enableAsyncProcessing) { this.enableAsyncProcessing = enableAsyncProcessing; }
    }
    
    // Main configuration getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public MatchingThresholds getThresholds() { return thresholds; }
    public void setThresholds(MatchingThresholds thresholds) { this.thresholds = thresholds; }
    
    public int getFuzzyDateRangeDays() { return fuzzyDateRangeDays; }
    public void setFuzzyDateRangeDays(int fuzzyDateRangeDays) { this.fuzzyDateRangeDays = fuzzyDateRangeDays; }
    
    public double getAmountTolerance() { return amountTolerance; }
    public void setAmountTolerance(double amountTolerance) { this.amountTolerance = amountTolerance; }
    
    public boolean isAutoApproveHighConfidence() { return autoApproveHighConfidence; }
    public void setAutoApproveHighConfidence(boolean autoApproveHighConfidence) { this.autoApproveHighConfidence = autoApproveHighConfidence; }
    
    public double getAutoApproveThreshold() { return autoApproveThreshold; }
    public void setAutoApproveThreshold(double autoApproveThreshold) { this.autoApproveThreshold = autoApproveThreshold; }
    
    public CleanupConfig getCleanup() { return cleanup; }
    public void setCleanup(CleanupConfig cleanup) { this.cleanup = cleanup; }
    
    public PerformanceConfig getPerformance() { return performance; }
    public void setPerformance(PerformanceConfig performance) { this.performance = performance; }
}
