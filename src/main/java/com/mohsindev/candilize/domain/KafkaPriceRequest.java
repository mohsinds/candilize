package com.mohsindev.candilize.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaPriceRequest {
    private String requestId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public class PriceObject {
        private String pair;
        private String interval;
        private int limit;
        private String exchange;
    }
    private LocalDateTime timestamp;
}
