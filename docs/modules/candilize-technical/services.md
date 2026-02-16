# Services & Data Models

## IndicatorService

**File**: `candilize-technical/.../indicator/IndicatorService.java`

### What This File Does

Computes technical indicators (SMA, EMA, RSI, etc.) using candle data fetched from the market service via gRPC.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorService {

    public List<IndicatorResult> computeSma(
            String pair,
            String interval,
            int period,
            int limit) {
        log.info("Computing SMA {} for {}/{}",
            period, pair, interval);
        // TODO: Call market gRPC for candles, compute SMA
        return List.of();
    }
}
```

**Status**: Stub implementation. Returns empty list.

**Planned implementation**:
1. Call `MarketGrpcClient.getCandles(pair, interval, limit + period)` to get enough data
2. Compute SMA: for each candle, average the closing prices of the previous `period` candles
3. Return `IndicatorResult` for each computed point

---

## ScannerService

**File**: `candilize-technical/.../scanner/ScannerService.java`

### What This File Does

Scans the market for symbols that meet specific criteria (e.g., SMA crossover, RSI oversold).

```java
@Slf4j
@Service
public class ScannerService {

    public List<ScanResult> scan(
            String criteria,
            String interval) {
        log.info("Scanning for criteria={}, interval={}",
            criteria, interval);
        return List.of();
    }
}
```

**Status**: Stub implementation. Returns empty list.

**Planned implementation**:
1. Fetch enabled pairs from the auth service config
2. For each pair, fetch candle data via `MarketGrpcClient`
3. Compute the relevant indicator (e.g., SMA crossover)
4. Return pairs that match the criteria

---

## StrategyService

**File**: `candilize-technical/.../strategy/StrategyService.java`

### What This File Does

Generates trading signals (BUY/SELL/HOLD) by applying a named strategy to candle data.

```java
@Slf4j
@Service
public class StrategyService {

    public List<StrategySignal> getSignals(
            String strategy,
            String pair,
            String interval) {
        log.info("Getting signals for strategy={}, pair={}, interval={}",
            strategy, pair, interval);
        return List.of();
    }
}
```

**Status**: Stub implementation. Returns empty list.

**Planned implementation**:
1. Resolve the strategy name to a strategy engine (Strategy pattern)
2. Fetch candle data via `MarketGrpcClient`
3. Run the strategy engine against the candles
4. Emit BUY/SELL signals with timestamps and reasons

---

## BacktestService

**File**: `candilize-technical/.../backtest/BacktestService.java`

### What This File Does

Runs a historical simulation of a trading strategy against past candle data to evaluate its performance.

```java
@Slf4j
@Service
public class BacktestService {

    public BacktestResult run(BacktestRequest request) {
        log.info("Running backtest: strategy={}, pair={}, interval={}",
            request.strategy(), request.pair(),
            request.interval());
        return new BacktestResult(0, 0, 0, List.of());
    }
}
```

**Status**: Stub implementation. Returns zeroed result.

**Planned implementation**:
1. Fetch historical candles for the date range via `MarketGrpcClient`
2. Apply the strategy to generate signals
3. Simulate trades based on signals
4. Calculate performance metrics: total return, win rate, trade count

---

## Data Model Records

All data models in this module use **Java records** — immutable data classes introduced in Java 16. Records auto-generate constructors, getters, `equals()`, `hashCode()`, and `toString()`.

### IndicatorResult

**File**: `candilize-technical/.../indicator/IndicatorResult.java`

```java
public record IndicatorResult(
    String name,                // e.g., "SMA_20", "RSI_14"
    BigDecimal value,           // computed indicator value
    Instant timestamp           // data point time
) {}
```

### ScanResult

**File**: `candilize-technical/.../scanner/ScanResult.java`

```java
public record ScanResult(
    String symbol,              // e.g., "BTCUSDT"
    String criterion,           // e.g., "sma_crossover"
    BigDecimal value            // metric value
) {}
```

### StrategySignal

**File**: `candilize-technical/.../strategy/StrategySignal.java`

```java
public record StrategySignal(
    String symbol,              // e.g., "BTCUSDT"
    String signal,              // "BUY", "SELL", "HOLD"
    Instant timestamp,          // when signal was generated
    String reason               // e.g., "SMA 20 crossed above SMA 50"
) {}
```

### BacktestRequest

**File**: `candilize-technical/.../backtest/BacktestRequest.java`

```java
public record BacktestRequest(
    String strategy,            // strategy name
    String pair,                // trading pair
    String interval,            // candle interval
    long startTime,             // start timestamp (ms)
    long endTime                // end timestamp (ms)
) {}
```

### BacktestResult

**File**: `candilize-technical/.../backtest/BacktestResult.java`

```java
public record BacktestResult(
    double totalReturn,         // total return as percentage
    double winRate,             // win rate (0.0 to 1.0)
    int totalTrades,            // number of trades executed
    List<Trade> trades          // list of individual trades
) {}
```

### Trade

**File**: `candilize-technical/.../backtest/Trade.java`

```java
public record Trade(
    String symbol,              // trading pair
    String side,                // "BUY" or "SELL"
    double price,               // execution price
    double quantity,            // trade quantity
    Instant timestamp           // execution time
) {}
```

---

## Service Dependency Graph

```
IndicatorController  ->  IndicatorService  ->  MarketGrpcClient  ->  market:9091
ScannerController    ->  ScannerService    ->  MarketGrpcClient  ->  market:9091
StrategyController   ->  StrategyService   ->  MarketGrpcClient  ->  market:9091
BacktestController   ->  BacktestService   ->  MarketGrpcClient  ->  market:9091

All controllers -> JwtAuthenticationFilter -> AuthGrpcClient -> auth:9090
```

All four services follow the same pattern: receive request from controller, fetch candle data via gRPC from the market service, compute results, return to controller.
