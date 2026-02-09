package com.mohsindev.candilize.infrastructure.market.binance;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.market.KlineToOhlcvAdapter;
import com.mohsindev.candilize.infrastructure.market.binance.dto.BinanceKline;
import org.springframework.stereotype.Component;

/**
 * Adapter: converts Binance-specific BinanceKline DTO to domain Ohlcv.
 */
@Component
public class BinanceKlineToOhlcvAdapter implements KlineToOhlcvAdapter<BinanceKline> {

    @Override
    public Ohlcv toOhlcv(BinanceKline kline, CandleInterval interval) {
        return Ohlcv.builder()
                .interval(interval)
                .timestamp(kline.getOpenTime())
                .open(kline.getOpen())
                .high(kline.getHigh())
                .low(kline.getLow())
                .close(kline.getClose())
                .volume(kline.getVolume())
                .build();
    }
}
