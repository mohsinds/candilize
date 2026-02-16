# CandleController — Query Candle Data

**File**: `candilize-market/.../api/controller/CandleController.java`

## What This File Does

The main read endpoint for candle data. Clients send a GET request with a trading pair and interval, and receive OHLCV candle data from MongoDB (with Redis caching).

## Full Source with Commentary

```java
@RestController                                                // 1
@RequestMapping("/api/v1/candles")                             // 2
@RequiredArgsConstructor
public class CandleController {

    private final CandleQueryService candleQueryService;       // 3

    @GetMapping("/{pair}/{interval}")                           // 4
    public ResponseEntity<List<CandleResponse>> getCandles(
            @PathVariable String pair,                         // 5
            @PathVariable String interval,
            @RequestParam(defaultValue = "100") int limit,     // 6
            @RequestParam(required = false) Long startTime,    // 7
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) String exchange) { // 8
        List<CandleResponse> candles =
            candleQueryService.getCandles(
                pair, interval, limit,
                startTime, endTime, exchange);
        return ResponseEntity.ok(candles);                     // 9
    }

    @GetMapping("/{pair}")                                      // 10
    public ResponseEntity<List<String>>
            getAvailableIntervals(@PathVariable String pair) {
        List<String> intervals =
            candleQueryService
                .getAvailableIntervalsForPair(pair);
        return ResponseEntity.ok(intervals);
    }
}
```

| # | What it does |
|---|---|
| 1 | `@RestController` — combines `@Controller` + `@ResponseBody`. Return values are serialized to JSON. |
| 2 | `@RequestMapping("/api/v1/candles")` — base path for all endpoints. |
| 3 | `CandleQueryService` — handles MongoDB queries, Redis caching, and pair/interval validation. |
| 4 | `@GetMapping("/{pair}/{interval}")` — full path: `/api/v1/candles/BTCUSDT/1h`. |
| 5 | `@PathVariable String pair` — extracts `BTCUSDT` from the URL. |
| 6 | `@RequestParam(defaultValue = "100") int limit` — optional, defaults to 100 candles. |
| 7 | `@RequestParam(required = false) Long startTime` — optional Unix timestamp (ms) for range filtering. `Long` wrapper allows `null`. |
| 8 | `@RequestParam(required = false) String exchange` — optional exchange filter (e.g., `binance`). |
| 9 | Returns HTTP 200 with JSON array of candles. |
| 10 | `GET /api/v1/candles/{pair}` — returns available interval codes (e.g., `["1m","5m","1h"]`) for a pair. Uses `MongoTemplate.findDistinct()`. |

## CandleResponse DTO

```java
public record CandleResponse(
    String symbol,            // e.g., "BTCUSDT"
    String intervalCode,      // e.g., "1h"
    long openTime,            // Unix timestamp (ms)
    BigDecimal openPrice,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    BigDecimal closePrice,
    BigDecimal volume,
    long closeTime,           // Unix timestamp (ms)
    String exchange           // e.g., "binance"
) {}
```

`BigDecimal` is used instead of `double` for prices because floating-point arithmetic loses precision (`0.1 + 0.2 == 0.30000000000000004` in double). `BigDecimal` preserves exact decimal values — critical for financial data.

## Request Flow

```
1. Client: GET /api/v1/candles/BTCUSDT/1h?limit=50
2. JwtAuthenticationFilter validates JWT via gRPC → auth:9090
3. SecurityConfig allows /api/v1/candles/** for authenticated users
4. CandleController.getCandles(pair="BTCUSDT", interval="1h", limit=50)
5. CandleQueryService.getCandles():
   a. @Cacheable checks Redis → cache hit → return immediately
   b. ConfigValidationService validates pair/interval against auth config
   c. MongoDB query: candle_data, sorted by openTime DESC, limit 50
   d. Maps CandleDataDocument → CandleResponse, stores in Redis
6. Returns HTTP 200 with JSON array
```

## Testing with cURL

```bash
# Get 100 candles (default)
curl -H "Authorization: Bearer <jwt>" \
  http://localhost:8080/api/v1/candles/BTCUSDT/1h

# With filters
curl -H "Authorization: Bearer <jwt>" \
  "http://localhost:8080/api/v1/candles/BTCUSDT/1h?limit=50&exchange=binance"

# Available intervals
curl -H "Authorization: Bearer <jwt>" \
  http://localhost:8080/api/v1/candles/BTCUSDT
```
