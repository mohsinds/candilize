package com.mohsindev.candilize.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class CandilizeAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandilizeAuthApplication.class, args);
    }
}
