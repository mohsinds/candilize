package com.mohsindev.candilize.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CandleResponse(
        String symbol,
        String intervalCode,
        long openTime,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal volume,
        long closeTime,
        String exchange
) {}
