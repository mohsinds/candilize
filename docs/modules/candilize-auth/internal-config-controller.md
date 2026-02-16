# InternalConfigController — Service-to-Service Config API

**File**: `candilize-auth/src/main/java/com/mohsindev/candilize/auth/api/controller/InternalConfigController.java`

## What This File Does

This controller exposes a single endpoint that the **market service** calls to fetch which trading pairs and intervals are currently enabled. It's secured with an API key (not JWT) because the caller is another microservice, not a human user.

---

## Full Source with Commentary

```java
/**
 * Internal API for market service. Secured by X-API-Key header.
 * Returns enabled pairs and intervals for the scheduler.
 */
@RestController
@RequestMapping("/api/v1/internal")                           // 1
@RequiredArgsConstructor
public class InternalConfigController {

    private final ConfigService configService;                // 2

    @GetMapping("/scheduler-config")                          // 3
    public ResponseEntity<SchedulerConfigResponse> getSchedulerConfig() {
        return ResponseEntity.ok(
            configService.getSchedulerConfig());              // 4
    }
}
```

### Line Explanations

**Line 1**: Base path is `/api/v1/internal`. In `SecurityConfig`, this path is restricted to `ROLE_SERVICE` — which is only granted by `InternalApiKeyFilter` when a valid `X-API-Key` header is present.

**Line 2**: Reuses the same `ConfigService` as `ConfigController`. The service layer doesn't know or care who's calling it — separation of concerns.

**Line 3**: `GET /api/v1/internal/scheduler-config` — the market service's scheduler calls this to know which pairs and intervals to download candle data for.

**Line 4**: `ConfigService.getSchedulerConfig()` queries the database for enabled pairs and intervals and returns them in a specific format.

---

## Response DTO

### SchedulerConfigResponse
```java
public record SchedulerConfigResponse(
    List<SchedulerPair> pairs,
    List<SchedulerInterval> intervals
) {
    public record SchedulerPair(String symbol, String baseAsset,
                                String quoteAsset) {}
    public record SchedulerInterval(String intervalCode) {}
}
```

**Nested records**: Java records can contain other records. `SchedulerPair` and `SchedulerInterval` are inner records — they only exist in the context of `SchedulerConfigResponse`. This is like defining a nested class.

Example response:
```json
{
  "pairs": [
    {"symbol": "BTCUSDT", "baseAsset": "BTC", "quoteAsset": "USDT"},
    {"symbol": "ETHUSDT", "baseAsset": "ETH", "quoteAsset": "USDT"}
  ],
  "intervals": [
    {"intervalCode": "1h"},
    {"intervalCode": "4h"}
  ]
}
```

---

## How the Market Service Calls This

In `candilize-market`, the `SchedulerConfigClient` class calls this endpoint:

```java
// In candilize-market:
@Component
public class SchedulerConfigClient {
    private final RestClient restClient;

    public SchedulerConfigResponse fetchConfig() {
        return restClient.get()
            .uri("/api/v1/internal/scheduler-config")
            .header("X-API-Key", apiKey)    // The API key
            .retrieve()
            .body(SchedulerConfigResponse.class);
    }
}
```

This is an example of **service-to-service communication over REST** (as opposed to gRPC which is used for token validation). The API key authenticates the market service as a trusted internal caller.

---

## Testing with cURL

```bash
# Must include X-API-Key header (default dev key)
curl http://localhost:8081/api/v1/internal/scheduler-config \
  -H "X-API-Key: internal-dev-key"

# Without the key → 401 Unauthorized
curl http://localhost:8081/api/v1/internal/scheduler-config
```
