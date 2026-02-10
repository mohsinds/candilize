package com.mohsindev.candilize.configuration;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * This configuration class is responsible to bootstrap kafka service
 * by connecting with the kafka service mentioned in the properties file.
 */
@Configuration
public class KafkaAdminConfig {

    @Bean
    public KafkaAdmin kafkaAdmin(KafkaProperties kafkaProperties) {
        return new KafkaAdmin(kafkaProperties.buildAdminProperties());
    }
}