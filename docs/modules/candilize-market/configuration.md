# Configuration — CacheConfig, KafkaConfig, WebClientConfig, and More

## CacheConfig — Redis Caching

**File**: `candilize-market/.../configuration/CacheConfig.java`

### What This File Does

Configures Redis as the caching backend with two named caches.

```java
@Configuration
public class CacheConfig {

    @Value("${app.cache.candle-ttl:60}")
    private long candleTtlSeconds;                             // 1

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());           // 2
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY);                         // 3

        GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(mapper);

        Map<String, RedisCacheConfiguration> cacheConfigs =
            new HashMap<>();
        cacheConfigs.put("candles", ...                        // 4
            .entryTtl(Duration.ofSeconds(candleTtlSeconds)));
        cacheConfigs.put("schedulerConfig", ...                // 5
            .entryTtl(Duration.ofSeconds(30)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}
```

| # | What it does |
|---|---|
| 1 | Candle cache TTL from properties. Default 60 seconds. |
| 2 | `JavaTimeModule` — required for serializing `Instant`, `BigDecimal` in cached objects. |
| 3 | `activateDefaultTyping` — stores Java type info in JSON so Redis can deserialize back to the correct type. Required for `GenericJackson2JsonRedisSerializer`. |
| 4 | `"candles"` cache — stores `CandleQueryService.getCandles()` results. TTL: `app.cache.candle-ttl`. |
| 5 | `"schedulerConfig"` cache — stores auth service config. Fixed 30s TTL. |

### Cache Usage

| Cache | Used by | TTL | Key pattern |
|---|---|---|---|
| `candles` | `CandleQueryService.getCandles()` | 60s (configurable) | `pair:interval:start:end:limit:exchange` |
| `schedulerConfig` | `ConfigValidationService.getCachedConfig()` | 30s (fixed) | `'config'` |

---

## KafkaConfig — Producer & Consumer

**File**: `candilize-market/.../configuration/KafkaConfig.java`

### What This File Does

Configures Kafka producer and consumer for the candle download pipeline.

```java
@EnableKafka @Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        // Key: StringSerializer
        // Value: JacksonJsonSerializer
        // ACKS: "all" (all brokers acknowledge)
        // Retries: 3
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        // Key: StringDeserializer
        // Value: ErrorHandlingDeserializer wrapping
        //        JacksonJsonDeserializer
        // Default type: KafkaPriceRequest
        // Auto-commit: enabled
        // Offset reset: "earliest"
    }
}
```

### Producer Settings

| Setting | Value | Why |
|---|---|---|
| Key serializer | `StringSerializer` | Key is the trading pair (string) |
| Value serializer | `JacksonJsonSerializer` | Serializes `KafkaPriceRequest` to JSON |
| ACKS | `"all"` | All in-sync replicas must acknowledge — prevents data loss |
| Retries | 3 | Retries on transient failures |

### Consumer Settings

| Setting | Value | Why |
|---|---|---|
| Value deserializer | `ErrorHandlingDeserializer` | Wraps `JacksonJsonDeserializer` — bad messages don't crash the consumer |
| Default type | `KafkaPriceRequest` | Deserializes to this class |
| Auto-commit | `true` | Offsets committed automatically after processing |
| Offset reset | `"earliest"` | On first start, reads from beginning of topic |

---

## ExchangeProperties — Type-Safe Config

**File**: `candilize-market/.../configuration/ExchangeProperties.java`

```java
@ConfigurationProperties(prefix = "exchange")
public record ExchangeProperties(
    Mexc mexc,                    // exchange.mexc.base-url
    Binance binance,              // exchange.binance.base-url
    ExchangeName defaultExchange  // exchange.default-exchange
) {
    public record Mexc(String baseUrl) {}
    public record Binance(String baseUrl) {}
}
```

Binds `exchange.*` properties to a type-safe record. More robust than `@Value` — compile-time field names, nested structure, IDE support.

---

## WebClientConfig — HTTP Clients

**File**: `candilize-market/.../configuration/WebClientConfig.java`

```java
@Configuration
public class WebClientConfig {

    @Bean("authServiceWebClient")
    WebClient authServiceWebClient(
            @Value("${app.auth-service.url}") String url) {
        return WebClient.builder().baseUrl(url).build();
    }

    @Bean("mexWebClient")
    WebClient mexWebClient(ExchangeProperties props) {
        return WebClient.builder()
            .baseUrl(props.mexc().baseUrl()).build();
    }

    @Bean("binanceWebClient")
    WebClient binanceWebClient(ExchangeProperties props) {
        return WebClient.builder()
            .baseUrl(props.binance().baseUrl()).build();
    }
}
```

| Bean | Base URL | Used by |
|---|---|---|
| `authServiceWebClient` | `http://localhost:8081` | `SchedulerConfigClient` (fetch config) |
| `mexWebClient` | MEXC API URL | `MexcDataClient` (fetch klines) |
| `binanceWebClient` | Binance API URL | `BinanceDataClient` (fetch klines) |

---

## JacksonConfig

Same as auth and technical modules — `ObjectMapper` with `JavaTimeModule` and ISO-8601 date formatting.

---

## OpenApiConfig — Swagger UI

Configures Swagger at `http://localhost:8080/swagger-ui.html` with Bearer JWT auth scheme. Title: "Candilize Market API".

---

## ExchangeNameConverter

```java
@Component
public class ExchangeNameConverter
        implements Converter<String, ExchangeName> {
    @Override
    public ExchangeName convert(String source) {
        return ExchangeName.parseCode(source);
    }
}
```

Spring `Converter` — allows `ExchangeName` as a `@RequestParam` type. Spring automatically converts the string parameter to the enum.

---

## GlobalExceptionHandler

**File**: `candilize-market/.../api/exception/GlobalExceptionHandler.java`

Centralized error handling for all REST controllers:

| Exception | HTTP Status | When |
|---|---|---|
| `EntityNotFoundException` | 404 | Pair/interval not found or disabled |
| `UnsupportedExchangeException` | 400 | Unknown exchange name |
| `IllegalArgumentException` | 400 | Invalid interval code, etc. |
| `MethodArgumentNotValidException` | 400 | `@Valid` fails (e.g., blank pair) |
| `Exception` (catch-all) | 500 | Unexpected errors |

All errors return the same `ErrorResponse` structure:
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Pair not found or disabled: INVALIDPAIR",
  "path": "/api/v1/candles/INVALIDPAIR/1h",
  "timestamp": "2025-01-15T10:30:00Z"
}
```
