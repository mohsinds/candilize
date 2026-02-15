package com.mohsindev.candilize.auth.infrastructure.enums;

import lombok.Getter;

import java.util.Arrays;

public enum ExchangeName {
    MEXC("mexc", "MEXC API"),
    BINANCE("binance", "Binance API");

    @Getter
    private final String code;

    @Getter
    private final String name;

    ExchangeName(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
