package com.mohsindev.candilize.domain;

import lombok.*;

import java.time.LocalDateTime;

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
