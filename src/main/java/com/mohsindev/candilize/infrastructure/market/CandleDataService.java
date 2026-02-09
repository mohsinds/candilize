package com.mohsindev.candilize.infrastructure.markets;

import java.util.List;

/**
 * Low-level interface for fetching raw kline data from an exchange.
 * Each exchange returns its own DTO type; CandleDataProvider + Adapter convert to Ohlcv.
 */
public interface CandleDataService<T> {
    List<T> getKlineData(String symbol, String interval, int limit);
}
