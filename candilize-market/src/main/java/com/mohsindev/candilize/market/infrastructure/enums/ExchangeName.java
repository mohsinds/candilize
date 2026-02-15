package com.mohsindev.candilize.market.infrastructure.enums;

import lombok.Getter;
import java.util.Arrays;

public enum ExchangeName {
    MEXC("mexc", "MEXC API"),
    BINANCE("binance", "Binance API"),
    TEST("test", "Test/Dummy API");

    @Getter
    private final String code;

    @Getter
    private final String name;

    ExchangeName(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ExchangeName parseCode(String code){
        return Arrays.stream(values())
                .filter(i -> i.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(""));
    }

}
