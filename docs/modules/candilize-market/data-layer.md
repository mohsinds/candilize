# Data Layer — MongoDB Document, Repositories, Exchange Providers

## CandleDataDocument — MongoDB Document

**File**: `candilize-market/.../infrastructure/persistence/document/CandleDataDocument.java`

### What This File Does

Maps to the `candle_data` MongoDB collection. Each document represents one OHLCV candle.

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "candle_data")                          // 1
@CompoundIndexes({
    @CompoundIndex(
        name = "uq_candle",
        def = "{'symbol': 1, 'intervalCode': 1, "
            + "'openTime': 1, 'exchange': 1}",
        unique = true)                                         // 2
})
public class CandleDataDocument {

    @Id
    private String id;                                         // 3

    private String symbol;          // e.g., "BTCUSDT"
    private String intervalCode;    // e.g., "1h"
    private Long openTime;          // Unix timestamp (ms)
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal volume;
    private Long closeTime;         // Unix timestamp (ms)
    private String exchange;        // e.g., "binance"
}
```

| # | What it does |
|---|---|
| 1 | `@Document(collection = "candle_data")` — maps to MongoDB collection. Unlike JPA `@Entity` (for relational DBs), `@Document` is for MongoDB. |
| 2 | **Compound unique index** — prevents duplicate candles. A candle is unique by (symbol + interval + openTime + exchange). `CandleDataPersistenceService` also does a programmatic check before saving. |
| 3 | `@Id` — MongoDB auto-generates a 24-character ObjectId string (e.g., `"65a1b2c3d4e5f6g7h8i9j0k1"`). |

### MongoDB vs JPA (MySQL)

| Feature | MongoDB (`@Document`) | JPA (`@Entity`) |
|---|---|---|
| Used in | candilize-market | candilize-auth |
| Database | MongoDB | MySQL |
| Schema | Flexible (no strict schema) | Strict (DDL + Flyway) |
| ID type | String (ObjectId) | Long (AUTO_INCREMENT) |
| Indexing | `@CompoundIndex` | `@Column(unique=true)` |
| Repository | `MongoRepository` | `JpaRepository` |

---

## Repositories

### CandleDataMongoRepository

**File**: `candilize-market/.../infrastructure/persistence/repository/CandleDataMongoRepository.java`

```java
public interface CandleDataMongoRepository
        extends MongoRepository<CandleDataDocument, String>,   // 1
                CandleDataMongoRepositoryCustom {               // 2

    List<CandleDataDocument>
        findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeDesc(
            String symbol, String intervalCode,
            Long openTimeStart, Long openTimeEnd,
            Pageable pageable);                                // 3

    List<CandleDataDocument>
        findBySymbolAndIntervalCodeAndOpenTimeBetweenAndExchangeOrderByOpenTimeDesc(
            String symbol, String intervalCode,
            Long openTimeStart, Long openTimeEnd,
            String exchange, Pageable pageable);                // 4

    boolean existsBySymbolAndIntervalCodeAndOpenTimeAndExchange(
        String symbol, String intervalCode,
        Long openTime, String exchange);                        // 5
}
```

| # | What it does |
|---|---|
| 1 | `MongoRepository<CandleDataDocument, String>` — provides `save()`, `findById()`, `findAll()`, `deleteById()`. |
| 2 | Extends custom interface for queries that can't be derived from method names. |
| 3 | Derived query — Spring parses the method name: `findBy` + `Symbol` AND `IntervalCode` AND `OpenTime` BETWEEN + `OrderBy` `OpenTime` DESC. With `Pageable` for limit. |
| 4 | Same but with additional `Exchange` filter. |
| 5 | `existsBy...` — used by `CandleDataPersistenceService` for deduplication before saving. |

### CandleDataMongoRepositoryImpl — Custom Query

```java
@Repository
@RequiredArgsConstructor
public class CandleDataMongoRepositoryImpl
        implements CandleDataMongoRepositoryCustom {

    private final MongoTemplate mongoTemplate;                 // 1

    @Override
    public List<String> findDistinctIntervalCodesBySymbol(
            String symbol) {
        return mongoTemplate.findDistinct(                     // 2
            Query.query(Criteria.where("symbol").is(symbol)),
            "intervalCode",
            CandleDataDocument.class,
            String.class);
    }
}
```

| # | What it does |
|---|---|
| 1 | `MongoTemplate` — lower-level MongoDB access. Used when derived queries aren't expressive enough. |
| 2 | `findDistinct("intervalCode")` — equivalent to `db.candle_data.distinct("intervalCode", {symbol: "BTCUSDT"})`. Returns `["1m", "5m", "1h"]`. |

---

## Exchange Providers — Strategy Pattern

### CandleDataProvider Interface

```java
public interface CandleDataProvider {
    ExchangeName getExchangeName();
    List<Ohlcv> getCandles(String pair,
        CandleInterval interval, int limit);
}
```

Each exchange implements this interface. The `CandleDataProviderFactory` selects the right implementation at runtime.

### CandleDataProviderFactory

```java
@Slf4j @Component
public class CandleDataProviderFactory {

    private final Map<String, CandleDataProvider>
        providersByExchange;                                   // 1
    private final boolean testingMode;
    private final CandleDataProvider testProvider;

    public CandleDataProviderFactory(
            List<CandleDataProvider> providers,                // 2
            @Value("${app.testing-mode:false}")
                boolean testingMode) {
        this.providersByExchange = providers.stream()
            .filter(p -> p.getExchangeName()
                != ExchangeName.TEST)
            .collect(Collectors.toUnmodifiableMap(
                p -> p.getExchangeName().getCode()
                    .toLowerCase(),
                Function.identity()));                         // 3
        this.testingMode = testingMode;
        this.testProvider = providers.stream()
            .filter(p -> p.getExchangeName()
                == ExchangeName.TEST)
            .findFirst().orElse(null);
    }

    public CandleDataProvider getProvider(
            String exchangeName) {
        if (testingMode) return testProvider;                   // 4
        CandleDataProvider provider =
            providersByExchange.get(
                exchangeName.toLowerCase());
        if (provider == null)
            throw new UnsupportedExchangeException(
                "Unsupported: " + exchangeName);               // 5
        return provider;
    }
}
```

| # | What it does |
|---|---|
| 1 | Map of exchange code → provider. e.g., `"binance" → BinanceCandleDataProvider`. |
| 2 | Spring injects ALL `CandleDataProvider` beans as a list. |
| 3 | Builds lookup map, excluding TestProvider. |
| 4 | In testing mode, always returns TestProvider (dummy data). |
| 5 | Throws if exchange is not supported. |

### BinanceCandleDataProvider

```java
@Component
public class BinanceCandleDataProvider
        implements CandleDataProvider {

    private final BinanceDataClient binanceDataClient;
    private final BinanceKlineToOhlcvAdapter adapter;

    @Override
    public ExchangeName getExchangeName() {
        return ExchangeName.BINANCE;
    }

    @Override
    public List<Ohlcv> getCandles(String pair,
            CandleInterval interval, int limit) {
        List<BinanceKline> klines =
            binanceDataClient.getKlineData(
                pair, interval.getCode(), limit);              // 1
        return klines.stream()
            .map(k -> adapter.toOhlcv(k, interval))           // 2
            .toList();
    }
}
```

| # | What it does |
|---|---|
| 1 | Fetches raw kline data from Binance REST API via `WebClient`. |
| 2 | Adapter pattern — converts `BinanceKline` (exchange-specific) to `Ohlcv` (domain model). |

### TestCandleDataProvider

Activated only when `app.testing-mode=true`. Generates synthetic OHLCV data with realistic price movements and time alignment. Useful for development without hitting exchange APIs.

---

## Domain Model

### Ohlcv

```java
@AllArgsConstructor @NoArgsConstructor @Builder @Data
public class Ohlcv {
    private CandleInterval interval;    // e.g., ONE_HOUR
    private Instant timestamp;          // open time
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
}
```

### CandleInterval Enum

```java
public enum CandleInterval {
    ONE_MIN("1m", 60),
    FIVE_MIN("5m", 300),
    ONE_HOUR("1h", 3600),
    ONE_DAY("1d", 86400),
    ONE_WEEK("1w", 604800),
    ONE_MONTH("1mo", 2592000);
    // ... all 11 intervals

    public static CandleInterval parseCode(String code) {
        // case-insensitive lookup
    }
}
```

### ExchangeName Enum

```java
public enum ExchangeName {
    MEXC("mexc", "MEXC API"),
    BINANCE("binance", "Binance API"),
    TEST("test", "Test/Dummy API");
}
```

### KafkaPriceRequest

```java
@Value @Builder
public class KafkaPriceRequest {
    String requestId;           // UUID
    PriceObject priceObject;    // pair, interval, limit, exchange
    LocalDateTime timestamp;

    @Value @Builder
    public static class PriceObject {
        String pair;
        String interval;
        int limit;
        String exchange;
    }
}
```

`@Value` (Lombok) makes all fields `final` and `private`, and generates getters. Combined with `@JsonCreator`/`@JsonProperty` for Kafka JSON deserialization.
