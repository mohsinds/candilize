package com.mohsindev.candilize.market.service;

import com.mohsindev.candilize.market.api.client.SchedulerConfigClient;
import com.mohsindev.candilize.market.api.client.SchedulerConfigResponse;
import com.mohsindev.candilize.market.api.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Validates pair and interval against auth service config. Caches scheduler config to reduce auth service calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigValidationService {

    private final SchedulerConfigClient schedulerConfigClient;

    @Cacheable(value = "schedulerConfig", key = "'config'")
    public Optional<SchedulerConfigResponse> getCachedConfig() {
        return schedulerConfigClient.fetchSchedulerConfig();
    }

    public void validatePair(String pair) {
        boolean enabled = getCachedConfig()
                .map(c -> c.pairs().stream().anyMatch(p -> p.symbol().equalsIgnoreCase(pair)))
                .orElse(false);
        if (!enabled) {
            throw new EntityNotFoundException("Pair not found or disabled: " + pair);
        }
    }

    public void validateInterval(String intervalCode) {
        boolean enabled = getCachedConfig()
                .map(c -> c.intervals().stream().anyMatch(i -> i.intervalCode().equalsIgnoreCase(intervalCode)))
                .orElse(false);
        if (!enabled) {
            throw new EntityNotFoundException("Interval not found or disabled: " + intervalCode);
        }
    }
}
