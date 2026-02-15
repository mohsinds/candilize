package com.mohsindev.candilize.market.api.client;

import java.util.List;

/** Response from auth service internal API for scheduler config. */
public record SchedulerConfigResponse(
        List<SchedulerPair> pairs,
        List<SchedulerInterval> intervals
) {
    public record SchedulerPair(String symbol, String baseAsset, String quoteAsset) {}
    public record SchedulerInterval(String intervalCode) {}
}
