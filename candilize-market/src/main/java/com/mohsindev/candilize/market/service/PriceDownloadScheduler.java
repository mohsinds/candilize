package com.mohsindev.candilize.market.service;

import com.mohsindev.candilize.market.api.client.SchedulerConfigClient;
import com.mohsindev.candilize.market.api.client.SchedulerConfigResponse;
import com.mohsindev.candilize.market.configuration.ExchangeProperties;
import com.mohsindev.candilize.market.domain.KafkaPriceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class PriceDownloadScheduler {

    private final SchedulerConfigClient schedulerConfigClient;
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
        Optional<SchedulerConfigResponse> configOpt = schedulerConfigClient.fetchSchedulerConfig();
        if (configOpt.isEmpty()) {
            log.warn("Skipping scheduler run for {} - could not fetch config from auth service", intervalCode);
            return;
        }

        SchedulerConfigResponse config = configOpt.get();
        if (config.intervals().stream().noneMatch(i -> i.intervalCode().equalsIgnoreCase(intervalCode))) {
            return;
        }

        List<SchedulerConfigResponse.SchedulerPair> pairs = config.pairs();
        String exchange = exchangeProperties.defaultExchange().getCode();

        for (SchedulerConfigResponse.SchedulerPair pair : pairs) {
            try {
                KafkaPriceRequest request = KafkaPriceRequest.builder()
                        .requestId(UUID.randomUUID().toString())
                        .priceObject(KafkaPriceRequest.PriceObject.builder()
                                .pair(pair.symbol())
                                .interval(intervalCode)
                                .limit(1)
                                .exchange(exchange)
                                .build())
                        .timestamp(LocalDateTime.now())
                        .build();
                kafkaProducerService.sendProducerRequest(request);
            } catch (Exception ex) {
                log.warn("Failed to schedule download for {} {}: {}", pair.symbol(), intervalCode, ex.getMessage());
            }
        }

        log.debug("Scheduled downloads for interval {}: {} pairs", intervalCode, pairs.size());
    }
}
