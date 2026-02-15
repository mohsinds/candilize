package com.mohsindev.candilize.market.api.controller;

import com.mohsindev.candilize.market.api.dto.request.DownloadRequest;
import com.mohsindev.candilize.market.domain.KafkaPriceRequest;
import com.mohsindev.candilize.market.service.KafkaProducerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/download")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DownloadController {

    private final KafkaProducerService kafkaProducerService;

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
