package com.mohsindev.candilize.service;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.enums.ExchangeName;

import java.time.Instant;
import java.util.List;

/**
 * Application service for candle/OHLCV data.
 * Controller depends on this abstraction; implementation delegates to the selected CandleDataProvider (Strategy).
 */
public interface CandleService {
    int downloadMarketData(String pair, CandleInterval interval, Instant from, Instant to, boolean forceDownload);
    List<Ohlcv> getMarketData(String pair, CandleInterval interval, Instant from, Instant to);

    /**
     * Fetches candles from the configured or specified exchange (Strategy selection).
     */
    List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit);
    List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit, ExchangeName exchange);
}
