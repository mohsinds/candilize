package com.mohsindev.candilize.infrastructure.market.mexc;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.enums.ExchangeName;
import com.mohsindev.candilize.infrastructure.market.CandleDataProvider;
import com.mohsindev.candilize.infrastructure.market.mexc.dto.MexcKline;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strategy implementation: MEXC exchange.
 * Uses MexcDataClient for raw klines and MexcKlineToOhlcvAdapter (Adapter pattern) to convert to Ohlcv.
 */
@Component
@Qualifier("mexcCandleDataProvider")
public class MexcCandleDataProvider implements CandleDataProvider {

    private final MexcDataClient mexcDataClient;
    private final MexcKlineToOhlcvAdapter adapter;

    public MexcCandleDataProvider(MexcDataClient mexcDataClient, MexcKlineToOhlcvAdapter adapter) {
        this.mexcDataClient = mexcDataClient;
        this.adapter = adapter;
    }

    @Override
    public ExchangeName getExchangeName() {
        return ExchangeName.MEXC;
    }

    @Override
    public List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit) {
        List<MexcKline> klines = mexcDataClient.getKlineData(pair, interval.getCode(), limit);
        return klines.stream()
                .map(k -> adapter.toOhlcv(k, interval))
                .toList();
    }
}
