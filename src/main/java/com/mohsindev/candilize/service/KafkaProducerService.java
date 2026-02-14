package com.mohsindev.candilize.service;

import com.mohsindev.candilize.domain.KafkaPriceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.price-request}")
    private String priceRequestTopic;

    public void sendProducerRequest(KafkaPriceRequest request) {
        log.info("Sending price request to Kafka topic={}, request={}", priceRequestTopic, request);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(priceRequestTopic, request.getPriceObject().getPair(), request);

        future.whenComplete((result, ex) -> {
           if(ex == null) {
               log.info("Message sent successfully: topic={}, partition={}, offset={}",
                       result.getRecordMetadata().topic(),
                       result.getRecordMetadata().partition(),
                       result.getRecordMetadata().offset());
           }
           else {
               log.error("Failed to send message to Kafka: {}", ex.getMessage());
           }
        });

//        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(priceRequestTopic, request, request);

    }

}
