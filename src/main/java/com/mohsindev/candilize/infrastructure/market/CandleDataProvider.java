package com.mohsindev.candilize.infrastructure.market;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.enums.ExchangeName;

import java.util.List;

/**
 * Strategy interface for fetching candle/OHLCV data from different exchanges.
 * Each exchange (MEXC, Binance, etc.) provides its own implementation.
 * The controller depends on the abstract CandleService, which delegates to the selected strategy.
 */
public interface CandleDataProvider {

    ExchangeName getExchangeName();

    /**
     * Fetches candle data for the given pair, interval and limit.
     *
     * @param pair    trading pair (e.g. BTCUSDT)
     * @param interval candle interval
     * @param limit   max number of candles to return
     * @return list of OHLCV candles in domain format
     */
    List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit);
}
