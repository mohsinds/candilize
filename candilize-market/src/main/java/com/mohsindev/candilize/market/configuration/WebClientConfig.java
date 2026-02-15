package com.mohsindev.candilize.market.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient beans for HTTP calls.
 * - authServiceWebClient: Calls candilize-auth (scheduler config via X-API-Key).
 * - mexWebClient, binanceWebClient: Call exchange APIs for candle data.
 */
@Configuration
public class WebClientConfig {

    /** WebClient for auth service REST calls (e.g. /api/v1/internal/scheduler-config). */
    @Bean("authServiceWebClient")
    WebClient authServiceWebClient(@Value("${app.auth-service.url}") String authServiceUrl) {
        return WebClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    /** WebClient for MEXC exchange public API. */
    @Bean("mexWebClient")
    WebClient mexWebClient(ExchangeProperties exchangeProperties) {
        return WebClient.builder()
                .baseUrl(exchangeProperties.mexc().baseUrl())
                .build();
    }

    /** WebClient for Binance exchange public API. */
    @Bean("binanceWebClient")
    WebClient binanceWebClient(ExchangeProperties exchangeProperties) {
        return WebClient.builder()
                .baseUrl(exchangeProperties.binance().baseUrl())
                .build();
    }
}
