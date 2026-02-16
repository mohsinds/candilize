package com.mohsindev.candilize.market.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaPriceRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void builder_createsRequestWithPriceObject() {
        KafkaPriceRequest.PriceObject priceObject = KafkaPriceRequest.PriceObject.builder()
                .pair("BTCUSDT")
                .interval("1m")
                .limit(100)
                .exchange("binance")
                .build();
        KafkaPriceRequest request = KafkaPriceRequest.builder()
                .requestId("req-1")
                .priceObject(priceObject)
                .timestamp(LocalDateTime.of(2026, 2, 15, 12, 0))
                .build();

        assertThat(request.getRequestId()).isEqualTo("req-1");
        assertThat(request.getPriceObject().getPair()).isEqualTo("BTCUSDT");
        assertThat(request.getPriceObject().getInterval()).isEqualTo("1m");
        assertThat(request.getPriceObject().getLimit()).isEqualTo(100);
        assertThat(request.getPriceObject().getExchange()).isEqualTo("binance");
    }

    @Test
    void jsonRoundTrip_deserializesFromJson() throws Exception {
        String json = """
                {"requestId":"r1","priceObject":{"pair":"ETHUSDT","interval":"5m","limit":50,"exchange":"binance"},"timestamp":"2026-02-15T12:00:00"}
                """;
        KafkaPriceRequest parsed = objectMapper.readValue(json, KafkaPriceRequest.class);

        assertThat(parsed.getRequestId()).isEqualTo("r1");
        assertThat(parsed.getPriceObject().getPair()).isEqualTo("ETHUSDT");
        assertThat(parsed.getPriceObject().getInterval()).isEqualTo("5m");
        assertThat(parsed.getPriceObject().getLimit()).isEqualTo(50);
    }
}
