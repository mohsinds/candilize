package com.mohsindev.candilize.technical.backtest;

public record BacktestRequest(String strategy, String pair, String interval, long startTime, long endTime) {}
