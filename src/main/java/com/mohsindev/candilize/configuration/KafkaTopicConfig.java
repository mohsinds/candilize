package com.mohsindev.candilize.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * This configuration class is responsible to create kafka topic if it is not created.
 */
@Configuration
public class KafkaTopicConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicConfig.class);

    @Value("${app.kafka.topic.price-request}")
    private String priceRequestTopic;

    @Bean
    public NewTopic getPriceTopic() {
        log.info("Creating topic bean: " + priceRequestTopic);

        return TopicBuilder.name(priceRequestTopic)
                .partitions(3)
                .replicas(1)
                .config("min.insync.replicas", "1")
                .config("retention.ms", String.valueOf(24L * 60 * 60 * 1000))
                .build();
    }
}
