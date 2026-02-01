package com.mohsindev.candilize.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient mexWebClient(ExchangeProperties exchangeProperties){
        return WebClient.builder()
                .baseUrl(exchangeProperties.mexc().baseUrl())
                .build();
    }

    @Bean
    WebClient binanceWebClient(ExchangeProperties exchangeProperties){
        return WebClient.builder()
                .baseUrl(exchangeProperties.binance().baseUrl())
                .build();
    }
}
