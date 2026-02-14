package com.mohsindev.candilize.api.controller;

import com.mohsindev.candilize.api.dto.request.DownloadRequest;
import com.mohsindev.candilize.domain.KafkaPriceRequest;
import com.mohsindev.candilize.service.KafkaProducerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-only controller for manually triggering candle downloads.
 * Publishes a KafkaPriceRequest to the get-price topic; the consumer fetches data and persists to DB.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/download")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DownloadController {

    private final KafkaProducerService kafkaProducerService;

    /** Triggers a one-off download for the given pair/interval/exchange. Request is processed asynchronously by Kafka consumer. */
    @PostMapping
    public ResponseEntity<Map<String, String>> triggerDownload(@Valid @RequestBody DownloadRequest request) {
        KafkaPriceRequest kafkaRequest = KafkaPriceRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .priceObject(KafkaPriceRequest.PriceObject.builder()
                        .pair(request.pair())
                        .interval(request.interval())
                        .limit(request.limit() != null ? request.limit() : 100)
                        .exchange(request.exchange())
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducerService.sendProducerRequest(kafkaRequest);
        log.info("Triggered download for {}/{} from {}", request.pair(), request.interval(), request.exchange());
        return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "message", "Download request submitted for " + request.pair() + "/" + request.interval()
        ));
    }

    /** Submits a backfill request (historical data). Uses limit to control how many candles to fetch. */
    @PostMapping("/backfill")
    public ResponseEntity<Map<String, String>> backfill(@Valid @RequestBody DownloadRequest request) {
        int limit = request.limit() != null ? request.limit() : 500;

        KafkaPriceRequest kafkaRequest = KafkaPriceRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .priceObject(KafkaPriceRequest.PriceObject.builder()
                        .pair(request.pair())
                        .interval(request.interval())
                        .limit(limit)
                        .exchange(request.exchange())
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducerService.sendProducerRequest(kafkaRequest);
        log.info("Triggered backfill for {}/{} from {} (limit={})", request.pair(), request.interval(), request.exchange(), limit);
        return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "message", "Backfill request submitted for " + request.pair() + "/" + request.interval() + " (limit=" + limit + ")"
        ));
    }
}
