package com.mohsindev.candilize.auth.api.dto.response;

import java.util.List;

/** Internal API response for market service to fetch enabled pairs and intervals. */
public record SchedulerConfigResponse(
        List<SchedulerPair> pairs,
        List<SchedulerInterval> intervals
) {
    public record SchedulerPair(String symbol, String baseAsset, String quoteAsset) {}
    public record SchedulerInterval(String intervalCode) {}
}
