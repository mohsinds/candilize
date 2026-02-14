package com.mohsindev.candilize.domain;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Message payload for the get-price Kafka topic. The consumer uses priceObject (pair, interval, limit, exchange)
 * to fetch candles from the appropriate CandleDataProvider and persist them to candle_data.
 */
@Value
@Builder
public class KafkaPriceRequest {
    String requestId;
    PriceObject priceObject;

    @Value
    @Builder
    public static class PriceObject {
        String pair;
        String interval;
        int limit;
        String exchange;
    }
    LocalDateTime timestamp;
}
