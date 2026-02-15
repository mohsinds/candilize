package com.mohsindev.candilize.technical.indicator;

import java.math.BigDecimal;
import java.time.Instant;

public record IndicatorResult(String name, BigDecimal value, Instant timestamp) {}
