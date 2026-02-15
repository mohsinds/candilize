package com.mohsindev.candilize.technical.backtest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for backtesting strategies against historical candle data.
 */
@Slf4j
@Service
public class BacktestService {

    public BacktestResult run(BacktestRequest request) {
        log.info("Running backtest: strategy={}, pair={}, interval={}", 
                request.strategy(), request.pair(), request.interval());
        return new BacktestResult(0, 0, 0, List.of());
    }
}
