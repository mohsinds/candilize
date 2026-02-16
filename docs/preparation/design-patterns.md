# Design Patterns in Candilize

Every design pattern used in the project, with exact class names, file paths, and code-level explanations.

---

## 1. Strategy Pattern

**Problem**: Multiple cryptocurrency exchanges (Binance, MEXC) have different APIs and response formats, but the download pipeline needs to treat them uniformly.

**Solution**: `CandleDataProvider` interface with exchange-specific implementations.

```
CandleDataProvider (interface)
├── BinanceCandleDataProvider   → calls Binance REST API
├── MexcCandleDataProvider      → calls MEXC REST API
└── TestCandleDataProvider      → generates synthetic data (dev/test)
```

**Interface** (`candilize-market/.../infrastructure/market/CandleDataProvider.java`):
```java
public interface CandleDataProvider {
    ExchangeName getExchangeName();
    List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit);
}
```

**Concrete strategy** (`candilize-market/.../infrastructure/market/binance/BinanceCandleDataProvider.java`):
```java
@Component
public class BinanceCandleDataProvider implements CandleDataProvider {
    @Override
    public ExchangeName getExchangeName() { return ExchangeName.BINANCE; }

    @Override
    public List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit) {
        List<BinanceKline> klines = binanceDataClient.getKlineData(pair, interval.getCode(), limit);
        return klines.stream().map(k -> adapter.toOhlcv(k, interval)).toList();
    }
}
```

**How it works at runtime**: `CandleDownloadService` calls `providerFactory.getProvider("binance")`, which returns the `BinanceCandleDataProvider`. The service calls `getCandles()` without knowing which exchange it's talking to.

**Adding a new exchange**: Implement `CandleDataProvider` + add an adapter class. Spring auto-discovers the new `@Component` bean. No changes to existing code.

---

## 2. Factory Pattern

**Problem**: The download pipeline needs to select the right exchange provider at runtime based on a string (e.g., `"binance"`).

**Solution**: `CandleDataProviderFactory` builds a map of all providers and looks up by exchange code.

**File**: `candilize-market/.../infrastructure/market/CandleDataProviderFactory.java`

```java
@Component
public class CandleDataProviderFactory {
    private final Map<String, CandleDataProvider> providersByExchange;
    private final boolean testingMode;
    private final CandleDataProvider testProvider;

    public CandleDataProviderFactory(
            List<CandleDataProvider> providers,           // Spring injects ALL implementations
            @Value("${app.testing-mode:false}") boolean testingMode) {
        this.providersByExchange = providers.stream()
            .filter(p -> p.getExchangeName() != ExchangeName.TEST)
            .collect(Collectors.toUnmodifiableMap(
                p -> p.getExchangeName().getCode().toLowerCase(),
                Function.identity()));
        this.testingMode = testingMode;
        this.testProvider = providers.stream()
            .filter(p -> p.getExchangeName() == ExchangeName.TEST)
            .findFirst().orElse(null);
    }

    public CandleDataProvider getProvider(String exchangeName) {
        if (testingMode) return testProvider;
        CandleDataProvider provider = providersByExchange.get(exchangeName.toLowerCase());
        if (provider == null) throw new UnsupportedExchangeException("Unsupported: " + exchangeName);
        return provider;
    }
}
```

**Key detail**: Spring injects `List<CandleDataProvider>` — all beans implementing the interface. The factory doesn't need to know about specific implementations at compile time.

---

## 3. Adapter Pattern

**Problem**: Exchange APIs return different data formats. Binance returns arrays-of-arrays (`BinanceKline`), MEXC returns a different JSON structure. The domain model expects `Ohlcv`.

**Solution**: Adapter classes convert exchange-specific DTOs to the domain model.

**File**: `candilize-market/.../infrastructure/market/binance/BinanceKlineToOhlcvAdapter.java`

```
BinanceKline (exchange-specific DTO)
    → BinanceKlineToOhlcvAdapter.toOhlcv()
    → Ohlcv (domain model)
```

The adapter extracts `open`, `high`, `low`, `close`, `volume`, and `timestamp` from the exchange-specific format and constructs an `Ohlcv` object.

**Why adapter (not just a constructor)?**
- Exchange response formats change independently of the domain model
- Each exchange needs different parsing logic
- The adapter can handle quirks (e.g., Binance returns timestamps in milliseconds, others might use seconds)

---

## 4. Observer Pattern (via Kafka)

**Problem**: The scheduler needs to trigger downloads for many pairs, but shouldn't block waiting for each one to complete.

**Solution**: Kafka decouples the trigger (producer) from the execution (consumer).

```
PriceDownloadScheduler (publisher)
    → KafkaProducerService.sendProducerRequest(KafkaPriceRequest)
    → [get-price topic] (event channel)
    → KafkaConsumerService.consumePriceRequest() (subscriber)
    → CandleDownloadService.downloadAndPersist()
```

**Files**:
- Publisher: `candilize-market/.../service/KafkaProducerService.java`
- Subscriber: `candilize-market/.../service/KafkaConsumerService.java`

**How it maps to Observer**:
- **Subject** = Kafka topic (`get-price`)
- **Observer** = `@KafkaListener` method in `KafkaConsumerService`
- **Event** = `KafkaPriceRequest` message
- **Notification** = Kafka delivers messages to all consumers in the group

This is a distributed, persistent version of the Observer pattern — messages survive service restarts and can be replayed.

---

## 5. Builder Pattern

**Problem**: Objects with many fields (DTOs, protobuf messages, domain models) need clean construction without telescoping constructors.

**Solution**: Lombok `@Builder` for application code, protobuf builders for generated code.

**Lombok `@Builder`** (domain model):
```java
@Builder @Data @AllArgsConstructor @NoArgsConstructor
public class Ohlcv {
    private CandleInterval interval;
    private Instant timestamp;
    private BigDecimal open, high, low, close, volume;
}

// Usage:
Ohlcv candle = Ohlcv.builder()
    .interval(CandleInterval.ONE_HOUR)
    .timestamp(Instant.now())
    .open(new BigDecimal("42150.50"))
    .build();
```

**Lombok `@Value` + `@Builder`** (immutable):
```java
@Value @Builder
public class KafkaPriceRequest {
    String requestId;
    PriceObject priceObject;
    LocalDateTime timestamp;
}
```

`@Value` makes all fields `final` and `private`. Combined with `@Builder`, you get immutable objects with a fluent construction API.

**Protobuf builders** (generated):
```java
ValidateTokenResponse.newBuilder()
    .setValid(true)
    .setUsername("admin")
    .addRoles("ROLE_ADMIN")
    .build();
```

---

## 6. Interceptor Pattern

**Problem**: Need to add logging and metrics to every gRPC call without modifying each service method.

**Solution**: gRPC interceptors that wrap call handlers.

**Server interceptor** (`candilize-auth/.../grpc/GrpcServerLoggingInterceptor.java`):
```java
@GlobalServerInterceptor
public class GrpcServerLoggingInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        long start = System.nanoTime();
        // Wrap the call to log status and duration on close
        return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                long duration = (System.nanoTime() - start) / 1_000_000;
                log.info("gRPC server | {} | {} | {}ms", method, status.getCode(), duration);
                super.close(status, trailers);
            }
        }, headers);
    }
}
```

**Metrics interceptor** (`GrpcServerMetricsInterceptor.java`):
- Records `grpc.server.calls` counter (tagged by method + status)
- Records `grpc.server.call.duration` timer

`@GlobalServerInterceptor` / `@GlobalClientInterceptor` — Spring gRPC annotations that auto-register the interceptor for all services/channels without manual configuration.

---

## 7. Filter Chain Pattern

**Problem**: Every HTTP request needs JWT validation before reaching the controller.

**Solution**: `JwtAuthenticationFilter extends OncePerRequestFilter` — plugs into Spring Security's filter chain.

**File**: `candilize-market/.../security/JwtAuthenticationFilter.java`

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain) {
        String jwt = extractJwtFromRequest(request);
        if (jwt != null) {
            ValidateResult result = authGrpcClient.validateToken(jwt);
            if (result.valid()) {
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        result.username(), null,
                        result.roles().stream()
                            .map(SimpleGrantedAuthority::new).toList()));
            }
        }
        filterChain.doFilter(request, response);  // always continue the chain
    }
}
```

**Registration** (in `SecurityConfig`):
```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

The filter runs before Spring's default authentication filter. If the JWT is valid, it sets the security context. If not, the request continues unauthenticated — Spring Security's `authorizeHttpRequests` rules decide what to do.

---

## 8. Template Method Pattern

**Problem**: Spring's request processing lifecycle needs a standard structure with customizable steps.

**Solution**: `OncePerRequestFilter` defines the template (`doFilter` → `doFilterInternal`), subclasses override the abstract `doFilterInternal` method.

This is a framework-provided pattern — `JwtAuthenticationFilter` provides the JWT-specific logic while Spring handles:
- Ensuring the filter runs exactly once per request
- Managing the filter chain lifecycle
- Error propagation

---

## 9. Repository Pattern

**Problem**: Data access logic should be abstracted behind interfaces, decoupled from MongoDB/MySQL specifics.

**Solution**: Spring Data repositories.

**MongoDB** (`CandleDataMongoRepository extends MongoRepository`):
- Derived queries from method names: `findBySymbolAndIntervalCodeAndOpenTimeBetween...`
- Custom queries via `CandleDataMongoRepositoryCustom` + `MongoTemplate`

**JPA/MySQL** (`UserRepository extends JpaRepository`):
- `findByUsername()`, `existsByUsername()`, `existsByEmail()`
- Flyway manages schema, Hibernate validates

The service layer (e.g., `CandleQueryService`) depends on the repository interface, not the implementation. Spring Data generates the implementation at runtime.

---

## Pattern Summary

| Pattern | Classes | Module |
|---|---|---|
| Strategy | `CandleDataProvider`, `BinanceCandleDataProvider`, `MexcCandleDataProvider`, `TestCandleDataProvider` | market |
| Factory | `CandleDataProviderFactory` | market |
| Adapter | `BinanceKlineToOhlcvAdapter` | market |
| Observer | `KafkaProducerService`, `KafkaConsumerService` | market |
| Builder | `@Builder` on `Ohlcv`, `KafkaPriceRequest`, `CandleDataDocument`; protobuf `.newBuilder()` | market, proto |
| Interceptor | `GrpcServerLoggingInterceptor`, `GrpcServerMetricsInterceptor`, `GrpcClientLoggingInterceptor`, `GrpcClientMetricsInterceptor` | auth, market, technical |
| Filter Chain | `JwtAuthenticationFilter` | auth, market, technical |
| Template Method | `OncePerRequestFilter.doFilterInternal()` | auth, market, technical |
| Repository | `CandleDataMongoRepository`, `UserRepository`, `SupportedPairRepository`, `SupportedIntervalRepository` | auth, market |
