package com.mohsindev.candilize.market.api.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fetches scheduler config (enabled pairs/intervals) from auth service internal API.
 * Used by PriceDownloadScheduler and for pair/interval validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerConfigClient {

    private final WebClient authServiceWebClient;

    @Value("${app.internal.api-key}")
    private String apiKey;

    /**
     * Returns cached scheduler config. Callers should cache with short TTL to avoid hitting auth on every request.
     */
    public Optional<SchedulerConfigResponse> fetchSchedulerConfig() {
        try {
            SchedulerConfigResponse config = authServiceWebClient.get()
                    .uri("/api/v1/internal/scheduler-config")
                    .header("X-API-Key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(SchedulerConfigResponse.class)
                    .block();
            return Optional.ofNullable(config);
        } catch (WebClientResponseException e) {
            log.warn("Failed to fetch scheduler config from auth service: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error fetching scheduler config: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isPairEnabled(String symbol) {
        return fetchSchedulerConfig()
                .map(c -> c.pairs().stream().anyMatch(p -> p.symbol().equalsIgnoreCase(symbol)))
                .orElse(false);
    }

    public boolean isIntervalEnabled(String intervalCode) {
        return fetchSchedulerConfig()
                .map(c -> c.intervals().stream().anyMatch(i -> i.intervalCode().equalsIgnoreCase(intervalCode)))
                .orElse(false);
    }
}
