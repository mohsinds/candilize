package com.mohsindev.candilize.technical.strategy;

import java.time.Instant;

public record StrategySignal(String symbol, String signal, Instant timestamp, String reason) {}
