# Services — Query, Download, Persistence, Kafka, Scheduler

## CandleQueryService

**File**: `candilize-market/.../service/CandleQueryService.java`

### What This File Does

Fetches candle data from MongoDB with Redis caching. The primary read path for both REST and gRPC consumers.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleQueryService {

    private final CandleDataMongoRepository
        candleDataMongoRepository;                             // 1
    private final ConfigValidationService
        configValidationService;                               // 2

    @Cacheable(                                                // 3
        value = "candles",
        key = "#pair + ':' + #intervalCode + ':' + ... "
    )
    public List<CandleResponse> getCandles(
            String pair, String intervalCode, int limit,
            Long startTime, Long endTime, String exchange) {
        configValidationService.validatePair(pair);            // 4
        configValidationService.validateInterval(intervalCode);

        Long start = startTime != null ? startTime : 0L;      // 5
        Long end = endTime != null ? endTime : Long.MAX_VALUE;

        PageRequest page = PageRequest.of(0, limit,
            Sort.by(Sort.Direction.DESC, "openTime"));         // 6

        List<CandleDataDocument> docs;
        if (exchange != null && !exchange.isBlank()) {
            docs = candleDataMongoRepository
                .findBySymbolAndIntervalCodeAndOpenTimeBetweenAndExchangeOrderByOpenTimeDesc(
                    pair.toUpperCase(), intervalCode,
                    start, end, exchange.toLowerCase(), page); // 7
        } else {
            docs = candleDataMongoRepository
                .findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeDesc(
                    pair.toUpperCase(), intervalCode,
                    start, end, page);
        }
        return docs.stream().map(this::toResponse)
            .collect(Collectors.toList());                     // 8
    }

    public List<String> getAvailableIntervalsForPair(
            String pair) {
        configValidationService.validatePair(pair);
        return candleDataMongoRepository
            .findDistinctIntervalCodesBySymbol(
                pair.toUpperCase());                           // 9
    }
}
```

| # | What it does |
|---|---|
| 1 | MongoDB repository — Spring Data generates query implementations from method names. |
| 2 | Validates pair/interval against auth service config. Throws `EntityNotFoundException` if disabled. |
| 3 | `@Cacheable("candles")` — Spring checks Redis before running the method. Cache key includes all parameters to avoid stale data. TTL: `app.cache.candle-ttl` (default 60s). |
| 4 | Validates that the pair and interval are enabled in the auth service. |
| 5 | Default time range: `0` to `Long.MAX_VALUE` (all data). |
| 6 | `PageRequest.of(0, limit, Sort.DESC)` — first page, `limit` results, sorted by `openTime` descending (newest first). |
| 7 | Two query variants — with/without exchange filter. Method names follow Spring Data naming convention for auto-generated queries. |
| 8 | Maps MongoDB documents to response DTOs. |
| 9 | Uses custom repository method with `MongoTemplate.findDistinct()`. |

---

## CandleDownloadService

**File**: `candilize-market/.../service/CandleDownloadService.java`

### What This File Does

Downloads candle data from exchanges (Binance, MEXC) and persists to MongoDB. Called by `KafkaConsumerService`.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleDownloadService {

    private final CandleDataProviderFactory providerFactory;  // 1
    private final CandleDataPersistenceService
        persistenceService;

    @Retryable(                                                // 2
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000)
    )
    public int downloadAndPersist(
            KafkaPriceRequest.PriceObject po) {
        String exchange = po.getExchange() != null
            && !po.getExchange().isBlank()
            ? po.getExchange() : "binance";                    // 3

        CandleDataProvider provider =
            providerFactory.getProvider(exchange);              // 4
        CandleInterval candleInterval =
            CandleInterval.parseCode(po.getInterval());        // 5
        List<Ohlcv> candles = provider.getCandles(
            po.getPair(), candleInterval, po.getLimit());      // 6

        return persistenceService.persistCandles(
            candles, po.getPair(),
            po.getInterval(), exchange);                        // 7
    }
}
```

| # | What it does |
|---|---|
| 1 | Factory pattern — selects the right exchange provider (Binance, MEXC, Test). |
| 2 | `@Retryable` — if the exchange API fails, retry up to 3 times with 2-second backoff. |
| 3 | Default to Binance if no exchange specified. |
| 4 | Factory selects provider by exchange name (strategy pattern). |
| 5 | Parses interval string `"1h"` to `CandleInterval.ONE_HOUR` enum. |
| 6 | Calls exchange API (e.g., Binance REST API) and gets `Ohlcv` domain objects. |
| 7 | Persists to MongoDB, returns count of new candles saved (skips duplicates). |

---

## CandleDataPersistenceService

**File**: `candilize-market/.../service/CandleDataPersistenceService.java`

### What This File Does

Saves candle data to MongoDB, skipping duplicates. Evicts the Redis candle cache after saving.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleDataPersistenceService {

    private final CandleDataMongoRepository
        candleDataMongoRepository;

    @CacheEvict(value = "candles", allEntries = true)          // 1
    public int persistCandles(List<Ohlcv> candles,
            String symbol, String intervalCode,
            String exchange) {
        int saved = 0;
        long intervalMs = candles.isEmpty()
            ? CandleInterval.parseCode(intervalCode)
                .getSeconds() * 1000L
            : candles.get(0).getInterval()
                .getSeconds() * 1000L;                         // 2

        for (Ohlcv o : candles) {
            long openTimeMs =
                o.getTimestamp().toEpochMilli();
            long closeTimeMs = openTimeMs + intervalMs;        // 3

            if (candleDataMongoRepository
                .existsBySymbolAndIntervalCodeAndOpenTimeAndExchange(
                    symbol, intervalCode,
                    openTimeMs, exchange)) {
                continue;                                      // 4
            }

            CandleDataDocument doc =
                CandleDataDocument.builder()
                    .symbol(symbol)
                    .intervalCode(intervalCode)
                    .openTime(openTimeMs)
                    .openPrice(o.getOpen())
                    .highPrice(o.getHigh())
                    .lowPrice(o.getLow())
                    .closePrice(o.getClose())
                    .volume(o.getVolume())
                    .closeTime(closeTimeMs)
                    .exchange(exchange)
                    .build();
            candleDataMongoRepository.save(doc);               // 5
            saved++;
        }
        return saved;
    }
}
```

| # | What it does |
|---|---|
| 1 | `@CacheEvict("candles", allEntries=true)` — clears entire Redis candle cache after persisting. Next query hits MongoDB fresh. |
| 2 | Calculates close time: `openTime + intervalSeconds * 1000`. |
| 3 | `closeTimeMs` — computed from openTime + interval duration. |
| 4 | **Deduplication** — checks if a candle with the same (symbol, interval, openTime, exchange) already exists. Skips if so. |
| 5 | Saves to MongoDB `candle_data` collection. |

---

## KafkaProducerService

**File**: `candilize-market/.../service/KafkaProducerService.java`

### What This File Does

Sends `KafkaPriceRequest` messages to the `get-price` Kafka topic. Used by controllers and the scheduler.

```java
@Service @Slf4j @RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate; // 1

    @Value("${app.kafka.topic.price-request}")
    private String priceRequestTopic;                          // 2

    public void sendProducerRequest(KafkaPriceRequest request) {
        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(
                priceRequestTopic,
                request.getPriceObject().getPair(),            // 3
                request);

        future.whenComplete((result, ex) -> {                  // 4
            if (ex == null) {
                log.info("Message sent: topic={}, partition={}, offset={}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send: {}",
                    ex.getMessage());
            }
        });
    }
}
```

| # | What it does |
|---|---|
| 1 | `KafkaTemplate` — Spring's high-level Kafka producer API. Configured in `KafkaConfig`. |
| 2 | Topic name from `application.properties`. |
| 3 | **Message key = pair** — ensures all requests for the same pair go to the same Kafka partition (ordering guarantee). |
| 4 | Async callback — logs partition/offset on success, error on failure. The send is fire-and-forget from the caller's perspective. |

---

## KafkaConsumerService

**File**: `candilize-market/.../service/KafkaConsumerService.java`

### What This File Does

Consumes `KafkaPriceRequest` messages from the `get-price` topic and delegates to `CandleDownloadService`.

```java
@Slf4j @Service @RequiredArgsConstructor
public class KafkaConsumerService {

    private final CandleDownloadService candleDownloadService;

    @KafkaListener(                                            // 1
        topics = "${app.kafka.topic.price-request}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumePriceRequest(
            @Payload KafkaPriceRequest request,                // 2
            @Header(KafkaHeaders.RECEIVED_PARTITION)
                int partition,                                 // 3
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received: partition={}, offset={}, request={}",
            partition, offset, request);
        try {
            int saved = candleDownloadService
                .downloadAndPersist(
                    request.getPriceObject());                  // 4
            log.info("Persisted {} candles for {}/{}",
                saved,
                request.getPriceObject().getPair(),
                request.getPriceObject().getInterval());
        } catch (Exception ex) {
            log.error("Error processing: pair={} interval={}: {}",
                request.getPriceObject().getPair(),
                request.getPriceObject().getInterval(),
                ex.getMessage());                              // 5
        }
    }
}
```

| # | What it does |
|---|---|
| 1 | `@KafkaListener` — Spring auto-creates a consumer. Topic and group from properties. |
| 2 | `@Payload` — the deserialized `KafkaPriceRequest` (via `JacksonJsonDeserializer`). |
| 3 | `@Header` — extracts Kafka metadata (partition, offset) for logging. |
| 4 | Delegates to download service — fetches from exchange, persists to MongoDB. |
| 5 | Errors are logged but not re-thrown — prevents message reprocessing loops. |

---

## PriceDownloadScheduler

**File**: `candilize-market/.../service/PriceDownloadScheduler.java`

### What This File Does

Cron-based scheduler that triggers candle downloads for all enabled pairs. Fetches config from auth service, then sends Kafka messages for each (pair, interval) combination.

```java
@Slf4j @Service @RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.scheduler.enabled",
    havingValue = "true")                                      // 1
public class PriceDownloadScheduler {

    private final SchedulerConfigClient schedulerConfigClient;
    private final KafkaProducerService kafkaProducerService;
    private final ExchangeProperties exchangeProperties;

    @Scheduled(cron = "${app.scheduler.cron.1m}")              // 2
    public void schedule1m() { triggerForInterval("1m"); }

    @Scheduled(cron = "${app.scheduler.cron.5m}")
    public void schedule5m() { triggerForInterval("5m"); }

    @Scheduled(cron = "${app.scheduler.cron.1h}")
    public void schedule1h() { triggerForInterval("1h"); }
    // ... 1d, 1w, 1mo ...

    private void triggerForInterval(String intervalCode) {
        Optional<SchedulerConfigResponse> configOpt =
            schedulerConfigClient.fetchSchedulerConfig();      // 3
        if (configOpt.isEmpty()) return;

        SchedulerConfigResponse config = configOpt.get();
        if (config.intervals().stream().noneMatch(
                i -> i.intervalCode()
                    .equalsIgnoreCase(intervalCode))) {
            return;                                            // 4
        }

        String exchange = exchangeProperties
            .defaultExchange().getCode();
        for (var pair : config.pairs()) {                      // 5
            kafkaProducerService.sendProducerRequest(
                KafkaPriceRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .priceObject(KafkaPriceRequest.PriceObject
                        .builder()
                        .pair(pair.symbol())
                        .interval(intervalCode)
                        .limit(1)                              // 6
                        .exchange(exchange)
                        .build())
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }
}
```

| # | What it does |
|---|---|
| 1 | `@ConditionalOnProperty` — scheduler only starts when `app.scheduler.enabled=true`. |
| 2 | `@Scheduled(cron = ...)` — separate method for each interval. Cron expression from properties. |
| 3 | Fetches enabled pairs/intervals from auth service via REST (`/api/v1/internal/scheduler-config`). |
| 4 | If this interval isn't enabled in the config, skip. |
| 5 | For each enabled pair, sends a Kafka download request. |
| 6 | `limit(1)` — scheduler only downloads the latest candle (real-time data). Backfilling is done manually via `DownloadController`. |

---

## ConfigValidationService

**File**: `candilize-market/.../service/ConfigValidationService.java`

### What This File Does

Validates pair/interval against the auth service's scheduler config. Caches the config in Redis to avoid hitting auth on every request.

```java
@Slf4j @Service @RequiredArgsConstructor
public class ConfigValidationService {

    private final SchedulerConfigClient schedulerConfigClient;

    @Cacheable(value = "schedulerConfig", key = "'config'")    // 1
    public Optional<SchedulerConfigResponse> getCachedConfig() {
        return schedulerConfigClient.fetchSchedulerConfig();
    }

    public void validatePair(String pair) {                    // 2
        boolean enabled = getCachedConfig()
            .map(c -> c.pairs().stream()
                .anyMatch(p -> p.symbol()
                    .equalsIgnoreCase(pair)))
            .orElse(false);
        if (!enabled) {
            throw new EntityNotFoundException(
                "Pair not found or disabled: " + pair);
        }
    }

    public void validateInterval(String intervalCode) {        // 3
        // Same pattern — checks intervals list
    }
}
```

| # | What it does |
|---|---|
| 1 | `@Cacheable("schedulerConfig")` — config cached for 30 seconds (per `CacheConfig`). |
| 2 | Validates pair against enabled pairs. Throws `EntityNotFoundException` → 404 if disabled. |
| 3 | Same validation for intervals. |

---

## SchedulerConfigClient

**File**: `candilize-market/.../api/client/SchedulerConfigClient.java`

Fetches scheduler config from `candilize-auth` via REST.

```java
@Slf4j @Component @RequiredArgsConstructor
public class SchedulerConfigClient {

    private final WebClient authServiceWebClient;
    @Value("${app.internal.api-key}")
    private String apiKey;

    public Optional<SchedulerConfigResponse> fetchSchedulerConfig() {
        return Optional.ofNullable(
            authServiceWebClient.get()
                .uri("/api/v1/internal/scheduler-config")
                .header("X-API-Key", apiKey)                   // Internal API key auth
                .retrieve()
                .bodyToMono(SchedulerConfigResponse.class)
                .block());
    }
}
```

Uses `X-API-Key` header for internal service-to-service auth (separate from JWT). The auth service has an `InternalApiKeyFilter` that validates this key.

### SchedulerConfigResponse

```java
public record SchedulerConfigResponse(
    List<SchedulerPair> pairs,
    List<SchedulerInterval> intervals
) {
    public record SchedulerPair(
        String symbol, String baseAsset, String quoteAsset) {}
    public record SchedulerInterval(String intervalCode) {}
}
```
