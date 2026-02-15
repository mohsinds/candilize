package com.mohsindev.candilize.technical.backtest;

import java.time.Instant;

public record Trade(String symbol, String side, double price, double quantity, Instant timestamp) {}
