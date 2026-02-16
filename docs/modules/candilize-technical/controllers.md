# Controllers — Indicator, Scanner, Strategy, Backtest

## IndicatorController

**File**: `candilize-technical/.../api/controller/IndicatorController.java`

### What This File Does

Exposes REST endpoints for computing technical indicators (SMA, EMA, RSI, etc.) on candle data.

### Full Source with Commentary

```java
@RestController                                                // 1
@RequestMapping("/api/v1/indicator")                           // 2
@RequiredArgsConstructor
public class IndicatorController {

    private final IndicatorService indicatorService;           // 3

    @GetMapping("/{pair}/{interval}/sma")                      // 4
    public ResponseEntity<List<IndicatorResult>> getSma(
            @PathVariable String pair,                         // 5
            @PathVariable String interval,
            @RequestParam(defaultValue = "20") int period,     // 6
            @RequestParam(defaultValue = "100") int limit) {   // 7
        List<IndicatorResult> results =
            indicatorService.computeSma(pair, interval,
                period, limit);
        return ResponseEntity.ok(results);                     // 8
    }
}
```

| # | What it does |
|---|---|
| 1 | `@RestController` — combines `@Controller` + `@ResponseBody`. Return values are serialized to JSON. |
| 2 | `@RequestMapping("/api/v1/indicator")` — base path for all endpoints in this controller. |
| 3 | `IndicatorService` — injected via constructor (Lombok `@RequiredArgsConstructor`). |
| 4 | `@GetMapping("/{pair}/{interval}/sma")` — full path: `/api/v1/indicator/BTCUSDT/1h/sma`. |
| 5 | `@PathVariable String pair` — extracts `BTCUSDT` from the URL path. |
| 6 | `@RequestParam(defaultValue = "20") int period` — SMA period (e.g., 20-day SMA). Optional, defaults to 20. |
| 7 | `@RequestParam(defaultValue = "100") int limit` — max candles to compute on. Defaults to 100. |
| 8 | Returns 200 OK with list of `IndicatorResult` records. |

### Example Request

```
GET /api/v1/indicator/BTCUSDT/1h/sma?period=50&limit=200
Authorization: Bearer <jwt>
```

---

## ScannerController

**File**: `candilize-technical/.../api/controller/ScannerController.java`

### What This File Does

Scans the market for symbols that meet specific criteria (e.g., SMA crossover, breakout patterns).

```java
@RestController
@RequestMapping("/api/v1/scanner")
@RequiredArgsConstructor
public class ScannerController {

    private final ScannerService scannerService;               // 1

    @GetMapping                                                // 2
    public ResponseEntity<List<ScanResult>> scan(
            @RequestParam(defaultValue = "sma_crossover")
                String criteria,                               // 3
            @RequestParam(defaultValue = "1h")
                String interval) {                             // 4
        List<ScanResult> results =
            scannerService.scan(criteria, interval);
        return ResponseEntity.ok(results);
    }
}
```

| # | What it does |
|---|---|
| 1 | `ScannerService` — performs the actual market scanning. |
| 2 | `@GetMapping` — maps to `GET /api/v1/scanner`. No path variable — all params via query string. |
| 3 | `criteria` — the scan criteria. Examples: `sma_crossover`, `rsi_oversold`, `volume_spike`. Defaults to `sma_crossover`. |
| 4 | `interval` — candle interval to scan on. Defaults to `1h`. |

### Example Request

```
GET /api/v1/scanner?criteria=rsi_oversold&interval=4h
Authorization: Bearer <jwt>
```

---

## StrategyController

**File**: `candilize-technical/.../api/controller/StrategyController.java`

### What This File Does

Returns trading signals (BUY/SELL/HOLD) for a specific strategy applied to a trading pair and interval.

```java
@RestController
@RequestMapping("/api/v1/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategyService;

    @GetMapping("/{strategy}/{pair}/{interval}")               // 1
    public ResponseEntity<List<StrategySignal>> getSignals(
            @PathVariable String strategy,                     // 2
            @PathVariable String pair,
            @PathVariable String interval) {
        List<StrategySignal> signals =
            strategyService.getSignals(strategy, pair, interval);
        return ResponseEntity.ok(signals);
    }
}
```

| # | What it does |
|---|---|
| 1 | Three path variables — strategy name, pair, interval. Full path: `/api/v1/strategy/sma_crossover/BTCUSDT/1h`. |
| 2 | `strategy` — strategy name (e.g., `sma_crossover`, `rsi_divergence`, `macd_signal`). |

### Example Request

```
GET /api/v1/strategy/sma_crossover/ETHUSDT/4h
Authorization: Bearer <jwt>
```

---

## BacktestController

**File**: `candilize-technical/.../api/controller/BacktestController.java`

### What This File Does

Runs a historical backtest of a trading strategy and returns performance metrics.

```java
@RestController
@RequestMapping("/api/v1/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    @PostMapping                                               // 1
    public ResponseEntity<BacktestResult> runBacktest(
            @RequestBody BacktestRequest request) {            // 2
        BacktestResult result = backtestService.run(request);
        return ResponseEntity.ok(result);
    }
}
```

| # | What it does |
|---|---|
| 1 | `@PostMapping` — uses POST because the request has a complex body with multiple parameters. |
| 2 | `@RequestBody BacktestRequest` — JSON body deserialized to a record: `{strategy, pair, interval, startTime, endTime}`. |

### Example Request

```
POST /api/v1/backtest
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "strategy": "sma_crossover",
  "pair": "BTCUSDT",
  "interval": "1h",
  "startTime": 1704067200000,
  "endTime": 1706745600000
}
```

### Example Response

```json
{
  "totalReturn": 12.5,
  "winRate": 0.65,
  "totalTrades": 20,
  "trades": [
    {
      "symbol": "BTCUSDT",
      "side": "BUY",
      "price": 42000.0,
      "quantity": 0.1,
      "timestamp": "2025-01-02T00:00:00Z"
    }
  ]
}
```
