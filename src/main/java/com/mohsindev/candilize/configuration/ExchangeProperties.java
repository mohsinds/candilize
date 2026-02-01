package com.mohsindev.candilize.configuration;

import com.mohsindev.candilize.infrastructure.enums.ExchangeName;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exchange")
public record ExchangeProperties(Mexc mexc, Binance binance, ExchangeName defaultExchange) {
    public record Mexc(String baseUrl) {}
    public record Binance(String baseUrl) {}
}
