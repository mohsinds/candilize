package com.mohsindev.candilize.market.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("authServiceWebClient")
    WebClient authServiceWebClient(@Value("${app.auth-service.url}") String authServiceUrl) {
        return WebClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    @Bean("mexWebClient")
    WebClient mexWebClient(ExchangeProperties exchangeProperties) {
        return WebClient.builder()
                .baseUrl(exchangeProperties.mexc().baseUrl())
                .build();
    }

    @Bean("binanceWebClient")
    WebClient binanceWebClient(ExchangeProperties exchangeProperties) {
        return WebClient.builder()
                .baseUrl(exchangeProperties.binance().baseUrl())
                .build();
    }
}
