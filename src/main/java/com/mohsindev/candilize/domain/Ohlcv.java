package com.mohsindev.candilize.domain;

import com.mohsindev.candilize.enums.CandleInterval;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Domain model for a single OHLCV candle (open, high, low, close, volume, timestamp, interval). */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class Ohlcv {
    private CandleInterval interval;
    private Instant timestamp;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
}
