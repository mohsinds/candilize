package com.mohsindev.candilize.technical.indicator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for technical indicators (SMA, EMA, RSI, etc.).
 * Fetches candle data via gRPC from candilize-market and computes indicators.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorService {

    public List<IndicatorResult> computeSma(String pair, String interval, int period, int limit) {
        log.info("Computing SMA {} for {}/{}", period, pair, interval);
        // TODO: Call market gRPC for candles, compute SMA
        return List.of();
    }
}
