package com.mohsindev.candilize;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;

/** Entry point. Enables configuration property binding, caching (Redis), and retry (e.g. for Kafka consumer). */
@ConfigurationPropertiesScan
@EnableCaching
@EnableRetry
@SpringBootApplication
public class CandilizeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandilizeApplication.class, args);
    }

}
