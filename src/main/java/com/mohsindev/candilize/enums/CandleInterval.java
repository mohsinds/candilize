package com.mohsindev.candilize.enums;

import lombok.Getter;

import java.util.Arrays;

public enum CandleInterval {
    ONE_MIN("1m", 60),
    THREE_MIN("3m", 180),
    FIVE_MIN("5m", 300),
    FIFTEEN_MIN("15m", 900),
    THIRTY_MIN("30m", 1800),

    ONE_HOUR("1h", 3600),
    TWO_HOUR("2h", 7200),
    FOUR_HOUR("4h", 14400),

    ONE_DAY("1d", 86400),
    ONE_WEEK("1w", 604800),
    ONE_MONTH("1M", 2592000);

    @Getter
    private final String code;

    @Getter
    private final int seconds;

    CandleInterval(String code, int seconds) {
        this.code = code;
        this.seconds = seconds;
    }

    public static CandleInterval parseCode(String code){
        return Arrays.stream(values())
                .filter(i -> i.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid candle interval code: " + code));
    }

}
