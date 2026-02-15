package com.mohsindev.candilize.technical.scanner;

import java.math.BigDecimal;

public record ScanResult(String symbol, String criterion, BigDecimal value) {}
