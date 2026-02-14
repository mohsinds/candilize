package com.mohsindev.candilize.service;

import com.mohsindev.candilize.configuration.ExchangeProperties;
import com.mohsindev.candilize.domain.KafkaPriceRequest;
import com.mohsindev.candilize.infrastructure.persistence.entity.SupportedIntervalEntity;
import com.mohsindev.candilize.infrastructure.persistence.entity.SupportedPairEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class PriceDownloadScheduler {

    private final ConfigService configService;
    private final KafkaProducerService kafkaProducerService;
    private final ExchangeProperties exchangeProperties;

    @Scheduled(cron = "${app.scheduler.cron.1m}")
    public void schedule1m() {
        triggerForInterval("1m");
    }

    @Scheduled(cron = "${app.scheduler.cron.5m}")
    public void schedule5m() {
        triggerForInterval("5m");
    }

    @Scheduled(cron = "${app.scheduler.cron.15m}")
    public void schedule15m() {
        triggerForInterval("15m");
    }

    @Scheduled(cron = "${app.scheduler.cron.30m}")
    public void schedule30m() {
        triggerForInterval("30m");
    }

    @Scheduled(cron = "${app.scheduler.cron.1h}")
    public void schedule1h() {
        triggerForInterval("1h");
    }

    @Scheduled(cron = "${app.scheduler.cron.4h}")
    public void schedule4h() {
        triggerForInterval("4h");
    }

    @Scheduled(cron = "${app.scheduler.cron.1d}")
    public void schedule1d() {
        triggerForInterval("1d");
    }

    @Scheduled(cron = "${app.scheduler.cron.1w}")
    public void schedule1w() {
        triggerForInterval("1w");
    }

    @Scheduled(cron = "${app.scheduler.cron.1M}")
    public void schedule1M() {
        triggerForInterval("1M");
    }

    private void triggerForInterval(String intervalCode) {
        List<SupportedIntervalEntity> intervals = configService.getEnabledIntervals();
        if (intervals.stream().noneMatch(i -> i.getIntervalCode().equalsIgnoreCase(intervalCode))) {
            return;
        }

        List<SupportedPairEntity> pairs = configService.getEnabledPairs();
        String exchange = exchangeProperties.defaultExchange().getCode();

        for (SupportedPairEntity pair : pairs) {
            try {
                KafkaPriceRequest request = KafkaPriceRequest.builder()
                        .requestId(UUID.randomUUID().toString())
                        .priceObject(KafkaPriceRequest.PriceObject.builder()
                                .pair(pair.getSymbol())
                                .interval(intervalCode)
                                .limit(1)
                                .exchange(exchange)
                                .build())
                        .timestamp(LocalDateTime.now())
                        .build();
                kafkaProducerService.sendProducerRequest(request);
            } catch (Exception ex) {
                log.warn("Failed to schedule download for {} {}: {}", pair.getSymbol(), intervalCode, ex.getMessage());
            }
        }

        log.debug("Scheduled downloads for interval {}: {} pairs", intervalCode, pairs.size());
    }
}
