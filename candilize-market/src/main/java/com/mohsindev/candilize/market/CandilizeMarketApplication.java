package com.mohsindev.candilize.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@EnableCaching
@EnableRetry
@EnableScheduling
@SpringBootApplication
public class CandilizeMarketApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandilizeMarketApplication.class, args);
    }
}
