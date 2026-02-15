package com.mohsindev.candilize.market.infrastructure.market.mexc;

import com.mohsindev.candilize.market.domain.Ohlcv;
import com.mohsindev.candilize.market.enums.CandleInterval;
import com.mohsindev.candilize.market.infrastructure.market.KlineToOhlcvAdapter;
import com.mohsindev.candilize.market.infrastructure.market.mexc.dto.MexcKline;
import org.springframework.stereotype.Component;

/**
 * Adapter: converts MEXC-specific MexcKline DTO to domain Ohlcv.
 */
@Component
public class MexcKlineToOhlcvAdapter implements KlineToOhlcvAdapter<MexcKline> {

    @Override
    public Ohlcv toOhlcv(MexcKline kline, CandleInterval interval) {
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
