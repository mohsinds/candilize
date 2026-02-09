package com.mohsindev.candilize.infrastructure.market.binance;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.enums.ExchangeName;
import com.mohsindev.candilize.infrastructure.market.CandleDataProvider;
import com.mohsindev.candilize.infrastructure.market.binance.dto.BinanceKline;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strategy implementation: Binance exchange.
 * Uses BinanceDataClient for raw klines and BinanceKlineToOhlcvAdapter (Adapter pattern) to convert to Ohlcv.
 */
@Component
@Qualifier("binanceCandleDataProvider")
public class BinanceCandleDataProvider implements CandleDataProvider {

    private final BinanceDataClient binanceDataClient;
    private final BinanceKlineToOhlcvAdapter adapter;

    public BinanceCandleDataProvider(BinanceDataClient binanceDataClient, BinanceKlineToOhlcvAdapter adapter) {
        this.binanceDataClient = binanceDataClient;
        this.adapter = adapter;
    }

    @Override
    public ExchangeName getExchangeName() {
        return ExchangeName.BINANCE;
    }

    @Override
    public List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit) {
        List<BinanceKline> klines = binanceDataClient.getKlineData(pair, interval.getCode(), limit);
        return klines.stream()
                .map(k -> adapter.toOhlcv(k, interval))
                .toList();
    }
}
