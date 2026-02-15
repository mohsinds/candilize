package com.mohsindev.candilize.market.infrastructure.market.binance;

import com.mohsindev.candilize.market.domain.Ohlcv;
import com.mohsindev.candilize.market.enums.CandleInterval;
import com.mohsindev.candilize.market.infrastructure.market.KlineToOhlcvAdapter;
import com.mohsindev.candilize.market.infrastructure.market.binance.dto.BinanceKline;
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
