package com.mohsindev.candilize.technical.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for trading strategy execution and signals.
 */
@Slf4j
@Service
public class StrategyService {

    public List<StrategySignal> getSignals(String strategy, String pair, String interval) {
        log.info("Getting signals for strategy={}, pair={}, interval={}", strategy, pair, interval);
        return List.of();
    }
}
