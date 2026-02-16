# CacheController — Cache Refresh

**File**: `candilize-market/.../api/controller/CacheController.java`

## What This File Does

Provides an endpoint to trigger a cache refresh for candle data. Sends a download request to Kafka using the default exchange, which re-fetches data and clears the Redis cache.

## Full Source with Commentary

```java
@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
public class CacheController {

    private final KafkaProducerService kafkaProducerService;  // 1
    private final ExchangeProperties exchangeProperties;       // 2

    @GetMapping("/refresh/{pair}/{interval}/{limit}")          // 3
    public ResponseEntity<Map<String, String>> refresh(
            @PathVariable String pair,
            @PathVariable String interval,
            @PathVariable int limit) {                         // 4
        KafkaPriceRequest request = KafkaPriceRequest.builder()
            .requestId(UUID.randomUUID().toString())
            .priceObject(KafkaPriceRequest.PriceObject.builder()
                .pair(pair)
                .interval(interval)
                .limit(limit)
                .exchange(exchangeProperties
                    .defaultExchange().getCode())               // 5
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        kafkaProducerService.sendProducerRequest(request);
        return ResponseEntity.ok(Map.of(                       // 6
            "status", "accepted",
            "message", "Refresh request submitted for "
                + pair + "/" + interval));
    }
}
```

| # | What it does |
|---|---|
| 1 | `KafkaProducerService` — sends messages to the `get-price` topic. |
| 2 | `ExchangeProperties` — `@ConfigurationProperties(prefix="exchange")` record. Provides `defaultExchange`. |
| 3 | `GET /api/v1/cache/refresh/BTCUSDT/1h/50` — uses GET for simplicity (browser-triggerable). |
| 4 | Limit is a required path variable (not optional like in CandleController). |
| 5 | Uses default exchange from config instead of requiring caller to specify. |
| 6 | Returns 200 OK (not 202 like DownloadController). |

## CacheController vs DownloadController

| Aspect | CacheController | DownloadController |
|---|---|---|
| Access | Any authenticated user | Admin only |
| HTTP method | GET | POST |
| Exchange | Default from config | Caller specifies |
| Validation | No `@Valid` | `@Valid @RequestBody` |
| Purpose | Quick cache refresh | Manual download/backfill |
