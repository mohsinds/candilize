package com.mohsindev.candilize.technical.grpc;

import com.mohsindev.candilize.proto.market.GetCandlesRequest;
import com.mohsindev.candilize.proto.market.GetCandlesResponse;
import com.mohsindev.candilize.proto.market.MarketServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MarketGrpcClient {

    @GrpcClient("market")
    private MarketServiceGrpc.MarketServiceBlockingStub marketStub;

    public List<com.mohsindev.candilize.proto.market.Candle> getCandles(
            String pair, String intervalCode, int limit, Long startTime, Long endTime, String exchange) {
        try {
            GetCandlesRequest.Builder builder = GetCandlesRequest.newBuilder()
                    .setPair(pair)
                    .setIntervalCode(intervalCode)
                    .setLimit(limit > 0 ? limit : 100);
            if (startTime != null && startTime > 0) builder.setStartTime(startTime);
            if (endTime != null && endTime > 0) builder.setEndTime(endTime);
            if (exchange != null && !exchange.isBlank()) builder.setExchange(exchange);

            GetCandlesResponse response = marketStub.getCandles(builder.build());
            return response.getCandlesList();
        } catch (StatusRuntimeException e) {
            log.warn("Market gRPC call failed: {}", e.getStatus());
            throw new RuntimeException("Failed to fetch candles: " + e.getStatus().getDescription());
        }
    }
}
