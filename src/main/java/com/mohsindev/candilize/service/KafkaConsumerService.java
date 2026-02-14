package com.mohsindev.candilize.service;

import com.mohsindev.candilize.domain.KafkaPriceRequest;
import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.enums.ExchangeName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class KafkaConsumerService {

    CandleService candleService;

    public KafkaConsumerService(CandleService candleService) {
        this.candleService = candleService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.price-request}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumePriceRequest (
            @Payload KafkaPriceRequest request,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
            ) {
        log.info("Received message from partition={}, offset={}, request={}",
                partition, offset, request);

        try {
//            List<Ohlcv> candles = candleService.getCandles(
//                    request.getPriceObject().getPair(),
//                    CandleInterval.parseCode(request.getPriceObject().getInterval()),
//                    request.getPriceObject().getLimit(),
//                    ExchangeName.parseCode(request.getPriceObject().getExchange()));
//
//            log.info("Candles data: {}",candles);

        } catch (Exception ex) {
            log.error("Error occurred: {}", ex.getMessage());

        }

    }
}
