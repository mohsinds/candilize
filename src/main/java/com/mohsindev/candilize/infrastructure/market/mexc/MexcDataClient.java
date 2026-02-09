package com.mohsindev.candilize.infrastructure.market.mexc;

import com.mohsindev.candilize.infrastructure.market.CandleDataService;
import com.mohsindev.candilize.infrastructure.market.mexc.dto.MexcKline;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class MexcDataClient implements CandleDataService<MexcKline> {
    //https://api.mexc.com/api/v3/klines?symbol=BTCUSDT&interval=1m
    /**
     * 0	Open time
     * 1	Open
     * 2	High
     * 3	Low
     * 4	Close
     * 5	Volume
     * 6	Close time
     * 7	Quote asset volume
     */
    private final WebClient mexcClient;

    public MexcDataClient(@Qualifier("mexWebClient") WebClient mexcClient) {
        this.mexcClient = mexcClient;
    }

    private MexcKline mapToKline(List<Object> k){
        if(k.size() < 7)
            throw new IllegalArgumentException("Invalid kline payload: " + k);

        return MexcKline.builder()
                .openTime(Instant.ofEpochMilli(((Number) k.get(0)).longValue()))
                .open(new BigDecimal((k.get(1)).toString()))
                .high(new BigDecimal((k.get(2)).toString()))
                .low(new BigDecimal((k.get(3)).toString()))
                .close(new BigDecimal((k.get(4)).toString()))
                .volume(new BigDecimal((k.get(5)).toString()))
                .closeTime(Instant.ofEpochMilli(((Number) k.get(6)).longValue()))
                .build();


    }

    @Override
    public List<MexcKline> getKlineData(String symbol, String interval, int limit) {
        List<List<Object>> response = mexcClient.get().uri(uriBuilder -> uriBuilder
                    .path("/api/v3/klines")
                    .queryParam("symbol", symbol)
                    .queryParam("interval", interval)
                    .queryParam("limit", limit)
                    .build())
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        r -> r.bodyToMono(String.class)
                                .map(b -> new RuntimeException("MEXC 4xx: " + b))
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        r -> r.bodyToMono(String.class)
                                .map(b -> new RuntimeException("MEXC 4xx: " + b))
                )
                .bodyToMono(new ParameterizedTypeReference<List<List<Object>>>() {})
                .block();

        if(response == null) return List.of();

        return response.stream().map(this::mapToKline).toList();

    }
}
