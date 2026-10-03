package com.knowledgeapplication.api.ai.quota;

import java.time.ZoneId;

/** Operational budgets, not a hardcoded model/tier policy. */
public record AiQuotaProperties(boolean enabled, long requestsPerMinute, long inputTokensPerMinute,
        long requestsPerDay, double tokenEstimateCharsPerToken, ZoneId dailyResetZone, Budget background) {
    public record Budget(long requestsPerMinute, long inputTokensPerMinute, long requestsPerDay) {
        public Budget { validate(requestsPerMinute, inputTokensPerMinute, requestsPerDay); }
    }
    public AiQuotaProperties {
        validate(requestsPerMinute, inputTokensPerMinute, requestsPerDay);
        if (!Double.isFinite(tokenEstimateCharsPerToken) || tokenEstimateCharsPerToken <= 0 || dailyResetZone == null)
            throw new IllegalArgumentException("AI quota estimation/reset zone is invalid");
        if (background != null && (background.requestsPerMinute() > requestsPerMinute
                || background.inputTokensPerMinute() > inputTokensPerMinute || background.requestsPerDay() > requestsPerDay))
            throw new IllegalArgumentException("Background quota must not exceed global quota");
    }
    private static void validate(long rpm, long tpm, long rpd) {
        if (rpm < 1 || tpm < 1 || rpd < 1) throw new IllegalArgumentException("AI quota limits must be positive");
    }
    public Budget budget() { return new Budget(requestsPerMinute, inputTokensPerMinute, requestsPerDay); }
    public long estimate(long chars) { return Math.max(1, (long) Math.ceil(chars / tokenEstimateCharsPerToken)); }
}
