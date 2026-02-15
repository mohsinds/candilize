package com.mohsindev.candilize.technical.backtest;

import java.util.List;

public record BacktestResult(double totalReturn, double winRate, int totalTrades, List<Trade> trades) {}
