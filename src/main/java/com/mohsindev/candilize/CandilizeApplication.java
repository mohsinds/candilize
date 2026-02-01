package com.mohsindev.candilize;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class CandilizeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandilizeApplication.class, args);
    }

}
