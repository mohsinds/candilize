# MarketGrpcService — gRPC Candle Data Server

**File**: `candilize-market/.../grpc/MarketGrpcService.java`

## What This File Does

A gRPC server that listens on port 9091. The `candilize-technical` module calls it to fetch candle data for indicator calculations and backtesting.

## Full Source with Commentary

```java
@Slf4j
@Service                                                       // 1
@RequiredArgsConstructor
public class MarketGrpcService
        extends MarketServiceGrpc.MarketServiceImplBase {     // 2

    private final CandleQueryService candleQueryService;       // 3

    @Override
    public void getCandles(
            GetCandlesRequest request,                         // 4
            StreamObserver<GetCandlesResponse>
                responseObserver) {                            // 5
        try {
            List<CandleResponse> candles =
                candleQueryService.getCandles(                 // 6
                    request.getPair(),
                    request.getIntervalCode(),
                    request.getLimit() > 0
                        ? request.getLimit() : 100,
                    request.getStartTime() > 0
                        ? request.getStartTime() : null,
                    request.getEndTime() > 0
                        ? request.getEndTime() : null,
                    request.getExchange().isBlank()
                        ? null : request.getExchange());

            List<Candle> protoCandles = candles.stream()
                .map(this::toProto)                            // 7
                .toList();

            responseObserver.onNext(
                GetCandlesResponse.newBuilder()
                    .addAllCandles(protoCandles)
                    .build());                                 // 8
        } catch (Exception e) {
            log.warn("GetCandles gRPC failed: {}",
                e.getMessage());
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());                    // 9
            return;
        }
        responseObserver.onCompleted();                         // 10
    }

    private Candle toProto(CandleResponse r) {                // 11
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
```

| # | What it does |
|---|---|
| 1 | `@Service` — Spring bean. Spring gRPC auto-discovers it because it extends `MarketServiceImplBase`. |
| 2 | `extends MarketServiceGrpc.MarketServiceImplBase` — generated from `market.proto`. Makes this the server implementation. |
| 3 | Reuses `CandleQueryService` — same logic as the REST controller (MongoDB query + Redis cache). |
| 4 | `GetCandlesRequest` — protobuf message with pair, intervalCode, limit, optional startTime/endTime/exchange. |
| 5 | `StreamObserver` — gRPC's callback for sending responses. Call `onNext()` to send, `onCompleted()` to finish. |
| 6 | Delegates to `CandleQueryService` — validates pair/interval, queries MongoDB, uses Redis cache. |
| 7 | Converts `CandleResponse` (Java record) to `Candle` (protobuf message). |
| 8 | `onNext()` — sends the response. `addAllCandles()` adds the full list to the repeated field. |
| 9 | `onError()` — sends a gRPC error with `INTERNAL` status. **Different from auth's pattern** — see below. |
| 10 | `onCompleted()` — signals the response is complete. **Must always call this** or the client hangs. |
| 11 | `toProto()` — converts `BigDecimal` prices to strings. Protobuf doesn't have a BigDecimal type, so strings preserve precision. |

## Error Handling: Market vs Auth

| | AuthGrpcService | MarketGrpcService |
|---|---|---|
| Pattern | Errors in response body | gRPC status errors |
| On failure | `response.setValid(false)` | `responseObserver.onError(Status.INTERNAL)` |
| Client handling | `if (response.getValid())` | `try-catch StatusRuntimeException` |
| Why | Validation failures are expected | Data fetch failures are exceptional |

Auth uses "errors in response body" because invalid tokens are a normal flow. Market uses gRPC status errors because failures to fetch candle data are unexpected and should propagate as exceptions.

## Testing with grpcurl

```bash
grpcurl -plaintext \
  -d '{"pair":"BTCUSDT","intervalCode":"1h","limit":10}' \
  localhost:9091 com.mohsindev.candilize.proto.market.MarketService/GetCandles
```
