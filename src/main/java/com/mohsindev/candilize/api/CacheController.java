package com.mohsindev.candilize.api;

import com.mohsindev.candilize.configuration.ExchangeProperties;
import com.mohsindev.candilize.domain.KafkaPriceRequest;
import com.mohsindev.candilize.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Cache refresh controller. Authenticated users can trigger a refresh of candle data
 * for a specific pair/interval by publishing a request to Kafka. The consumer will fetch
 * from the exchange and persist to DB; candle cache entries may be evicted on write.
 */
@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
public class CacheController {

    private final KafkaProducerService kafkaProducerService;
    private final ExchangeProperties exchangeProperties;

    /** Triggers async refresh of candle data for the given pair/interval/limit using the default exchange. */
    @GetMapping("/refresh/{pair}/{interval}/{limit}")
    public ResponseEntity<Map<String, String>> refresh(
            @PathVariable String pair,
            @PathVariable String interval,
            @PathVariable int limit) {
        KafkaPriceRequest request = KafkaPriceRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .priceObject(KafkaPriceRequest.PriceObject.builder()
                        .pair(pair)
                        .interval(interval)
                        .limit(limit)
                        .exchange(exchangeProperties.defaultExchange().getCode())
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducerService.sendProducerRequest(request);
        return ResponseEntity.ok(Map.of("status", "accepted", "message", "Refresh request submitted for " + pair + "/" + interval));
    }
}
