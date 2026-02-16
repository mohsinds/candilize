# DownloadController — Admin-Only Download Triggers

**File**: `candilize-market/.../api/controller/DownloadController.java`

## What This File Does

Allows admins to manually trigger candle data downloads from exchanges. Exposes two POST endpoints: standard download and backfill (historical data). Both work asynchronously via Kafka.

## Full Source with Commentary

```java
@Slf4j
@RestController
@RequestMapping("/api/v1/download")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")                              // 1
public class DownloadController {

    private final KafkaProducerService kafkaProducerService;  // 2

    @PostMapping                                               // 3
    public ResponseEntity<Map<String, String>> triggerDownload(
            @Valid @RequestBody DownloadRequest request) {     // 4
        KafkaPriceRequest kafkaRequest =
            KafkaPriceRequest.builder()
                .requestId(UUID.randomUUID().toString())       // 5
                .priceObject(KafkaPriceRequest.PriceObject
                    .builder()
                    .pair(request.pair())
                    .interval(request.interval())
                    .limit(request.limit() != null
                        ? request.limit() : 100)               // 6
                    .exchange(request.exchange())
                    .build())
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducerService
            .sendProducerRequest(kafkaRequest);                // 7
        return ResponseEntity.accepted().body(Map.of(          // 8
            "status", "accepted",
            "message", "Download request submitted for "
                + request.pair() + "/" + request.interval()
        ));
    }

    @PostMapping("/backfill")                                  // 9
    public ResponseEntity<Map<String, String>> backfill(
            @Valid @RequestBody DownloadRequest request) {
        int limit = request.limit() != null
            ? request.limit() : 500;                           // 10
        // ... same pattern, sends to Kafka ...
        return ResponseEntity.accepted().body(Map.of(
            "status", "accepted",
            "message", "Backfill request submitted..."
        ));
    }
}
```

| # | What it does |
|---|---|
| 1 | `@PreAuthorize("hasRole('ADMIN')")` — class-level. Every method requires `ROLE_ADMIN`. Spring auto-prepends `ROLE_`, so checks for `ROLE_ADMIN` in SecurityContext. |
| 2 | Only dependency. Controller doesn't call exchange APIs directly — sends to Kafka. |
| 3 | `POST /api/v1/download` — trigger a standard download. |
| 4 | `@Valid` triggers bean validation. `@RequestBody` deserializes JSON to `DownloadRequest`. |
| 5 | `UUID.randomUUID()` — unique ID for tracing through the async pipeline. |
| 6 | Default limit 100 if not provided. |
| 7 | Fire-and-forget to Kafka `get-price` topic. |
| 8 | HTTP 202 Accepted — "received, will process later". More accurate than 200 for async operations. |
| 9 | `POST /api/v1/download/backfill` — historical data, higher default. |
| 10 | Backfill defaults to 500 candles (vs 100 for standard). |

## DownloadRequest DTO

```java
public record DownloadRequest(
    @NotBlank(message = "Pair is required") String pair,
    @NotBlank(message = "Interval is required") String interval,
    @NotBlank(message = "Exchange is required") String exchange,
    @PositiveOrZero Integer limit,
    Long startTime,
    Long endTime
) {}
```

`@Valid` + `@NotBlank` means Spring validates before the method runs. If `pair` is empty, throws `MethodArgumentNotValidException` → `GlobalExceptionHandler` → HTTP 400.

## Async Pipeline

```
POST /api/v1/download → DownloadController → KafkaProducerService
  → Kafka "get-price" topic → KafkaConsumerService
  → CandleDownloadService (@Retryable: 3 attempts, 2s backoff)
  → BinanceCandleDataProvider / MexcCandleDataProvider
  → CandleDataPersistenceService → MongoDB
```
