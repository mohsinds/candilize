package com.mohsindev.candilize.market.service;

import com.mohsindev.candilize.market.domain.KafkaPriceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Consumes KafkaPriceRequest from get-price topic.
 * Delegates to CandleDownloadService to fetch candles from exchange and persist to MongoDB.
 * Group ID ensures only one consumer processes each message (within consumer group).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final CandleDownloadService candleDownloadService;

    @KafkaListener(
            topics = "${app.kafka.topic.price-request}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumePriceRequest(
            @Payload KafkaPriceRequest request,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received message from partition={}, offset={}, request={}", partition, offset, request);

        try {
            int saved = candleDownloadService.downloadAndPersist(request.getPriceObject());
            log.info("Persisted {} candles for {}/{}",
                    saved,
                    request.getPriceObject().getPair(),
                    request.getPriceObject().getInterval());
        } catch (Exception ex) {
            log.error("Error processing price request for pair={} interval={}: {}",
                    request.getPriceObject().getPair(),
                    request.getPriceObject().getInterval(),
                    ex.getMessage());
        }
    }
}
