package com.mohsindev.candilize.configuration;

import com.mohsindev.candilize.infrastructure.enums.ExchangeName;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param mexc
 * @param binance
 * @param defaultExchange
 * This record is responsible to bind exchanges config with the configuration class.
 */
@ConfigurationProperties(prefix = "exchange")
public record ExchangeProperties(Mexc mexc, Binance binance, ExchangeName defaultExchange) {
    public record Mexc(String baseUrl) {}
    public record Binance(String baseUrl) {}
}
