package com.mohsindev.candilize.market.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    LocalDateTime timestamp;

    @JsonCreator
    public KafkaPriceRequest(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("priceObject") PriceObject priceObject,
            @JsonProperty("timestamp") LocalDateTime timestamp) {
        this.requestId = requestId;
        this.priceObject = priceObject;
        this.timestamp = timestamp;
    }

    @Value
    @Builder
    public static class PriceObject {
        String pair;
        String interval;
        int limit;
        String exchange;

        @JsonCreator
        public PriceObject(
                @JsonProperty("pair") String pair,
                @JsonProperty("interval") String interval,
                @JsonProperty("limit") int limit,
                @JsonProperty("exchange") String exchange) {
            this.pair = pair;
            this.interval = interval;
            this.limit = limit;
            this.exchange = exchange;
        }
    }
}
