package com.mohsindev.candilize.market.grpc;

import com.mohsindev.candilize.market.api.dto.response.CandleResponse;
import com.mohsindev.candilize.market.service.CandleQueryService;
import com.mohsindev.candilize.proto.market.Candle;
import com.mohsindev.candilize.proto.market.GetCandlesRequest;
import com.mohsindev.candilize.proto.market.GetCandlesResponse;
import com.mohsindev.candilize.proto.market.MarketServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * gRPC server for candle data (service-to-service).
 * Called by candilize-technical to fetch candles for indicators/backtesting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketGrpcService extends MarketServiceGrpc.MarketServiceImplBase {

    private final CandleQueryService candleQueryService;

    @Override
    public void getCandles(GetCandlesRequest request, StreamObserver<GetCandlesResponse> responseObserver) {
        try {
            List<CandleResponse> candles = candleQueryService.getCandles(
                    request.getPair(),
                    request.getIntervalCode(),
                    request.getLimit() > 0 ? request.getLimit() : 100,
                    request.getStartTime() > 0 ? request.getStartTime() : null,
                    request.getEndTime() > 0 ? request.getEndTime() : null,
                    request.getExchange().isBlank() ? null : request.getExchange());

            List<Candle> protoCandles = candles.stream()
                    .map(this::toProto)
                    .toList();

            responseObserver.onNext(GetCandlesResponse.newBuilder()
                    .addAllCandles(protoCandles)
                    .build());
        } catch (Exception e) {
            log.warn("GetCandles gRPC failed: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
            return;
        }
        responseObserver.onCompleted();
    }

    private Candle toProto(CandleResponse r) {
        return Candle.newBuilder()
                .setSymbol(r.symbol())
                .setIntervalCode(r.intervalCode())
                .setOpenTime(r.openTime())
                .setOpenPrice(r.openPrice().toString())
                .setHighPrice(r.highPrice().toString())
                .setLowPrice(r.lowPrice().toString())
                .setClosePrice(r.closePrice().toString())
                .setVolume(r.volume().toString())
                .setCloseTime(r.closeTime())
                .setExchange(r.exchange())
                .build();
    }
}
