package com.mohsindev.candilize.infrastructure.markets;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;

/**
 * Adapter interface: converts exchange-specific kline DTOs to the domain Ohlcv model.
 * Each exchange has its own kline format; implementations adapt that format to our domain.
 */
public interface KlineToOhlcvAdapter<T> {

    Ohlcv toOhlcv(T kline, CandleInterval interval);
}
