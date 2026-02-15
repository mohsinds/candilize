package com.mohsindev.candilize.market.configuration;

import com.mohsindev.candilize.market.infrastructure.enums.ExchangeName;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exchange")
public record ExchangeProperties(Mexc mexc, Binance binance, ExchangeName defaultExchange) {
    public record Mexc(String baseUrl) {}
    public record Binance(String baseUrl) {}
}
