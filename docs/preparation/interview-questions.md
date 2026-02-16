# Interview Questions — Candilize Project

Questions you might be asked about the Candilize project in a technical interview, organized by topic. Each answer references specific classes and design decisions.

---

## 1. Architecture & Design

### Q: Walk me through the architecture of your project.

Candilize is a cryptocurrency candle data platform with three microservices:

- **candilize-auth** (port 8081, gRPC 9090) — user authentication, JWT token management, and configuration (supported trading pairs/intervals). Uses MySQL for persistent data and Redis for caching.
- **candilize-market** (port 8080, gRPC 9091) — the core data pipeline. Downloads OHLCV candle data from exchange APIs (Binance, MEXC), stores it in MongoDB, and serves it via REST and gRPC. Uses Kafka for async download processing and Redis for query caching.
- **candilize-technical** (port 8082) — technical analysis. Consumes candle data from market via gRPC and provides indicators (SMA), scanner, strategy, and backtesting features. Currently a client-only service with no database.
- **candilize-proto** — shared module containing `.proto` files that define the gRPC contracts between services.

Communication patterns:
- **REST** for external clients and some internal calls (market → auth for scheduler config)
- **gRPC** for service-to-service calls (JWT validation, candle data fetching)
- **Kafka** for async candle download processing within the market service

### Q: Why microservices instead of a monolith?

Each service has different scaling needs and different databases:
- Auth is low-traffic (login/register) → MySQL for relational user data
- Market is high-volume (millions of candles) → MongoDB for document storage, Kafka for async processing
- Technical is compute-heavy (indicators, backtesting) → stateless, no database, horizontally scalable

They also have different deployment lifecycles — I can update the indicator logic without touching the auth or data pipeline code.

### Q: Why did you separate auth as its own service?

Centralized authentication gives a single source of truth for JWT validation. Market and technical never see the JWT signing secret — they validate tokens via gRPC to auth. This means:
- Only one service needs to be secured with the secret key
- Token revocation can be added in auth without changing any clients
- Auth can scale independently from the data pipeline

---

## 2. Design Patterns

### Q: What design patterns did you use?

| Pattern | Implementation | Purpose |
|---|---|---|
| **Strategy** | `CandleDataProvider` interface with `BinanceCandleDataProvider`, `MexcCandleDataProvider`, `TestCandleDataProvider` | Each exchange has its own implementation for fetching candle data |
| **Factory** | `CandleDataProviderFactory` | Selects the correct provider by exchange name at runtime |
| **Adapter** | `BinanceKlineToOhlcvAdapter` | Converts exchange-specific DTOs (`BinanceKline`) to domain model (`Ohlcv`) |
| **Observer** | Kafka producer/consumer | Decouples download triggers from download execution |
| **Builder** | Lombok `@Builder` on DTOs, protobuf message builders | Immutable object construction |
| **Interceptor** | `GrpcServerLoggingInterceptor`, `GrpcClientMetricsInterceptor` | Cross-cutting concerns (logging, metrics) for gRPC calls |
| **Filter Chain** | `JwtAuthenticationFilter extends OncePerRequestFilter` | HTTP request pipeline for authentication |
| **Template Method** | Spring's `OncePerRequestFilter.doFilterInternal()` | Framework defines the lifecycle, subclass provides the logic |

### Q: Explain the Strategy + Factory pattern in your exchange integration.

The `CandleDataProvider` interface defines a contract: `getCandles(pair, interval, limit)`. Each exchange implements it differently — Binance returns an array-of-arrays, MEXC returns a different JSON structure. The adapter converts each to the unified `Ohlcv` domain model.

`CandleDataProviderFactory` takes all `CandleDataProvider` beans (Spring injects them as a `List`), builds a `Map<String, CandleDataProvider>` keyed by exchange code, and returns the right one at runtime. There's also a `testingMode` flag that returns a `TestCandleDataProvider` with synthetic data for development without hitting real APIs.

Adding a new exchange is one class — implement `CandleDataProvider`, add an adapter, and it's auto-discovered by Spring.

### Q: Why Kafka for the download pipeline? Why not just call the API directly?

Direct API calls would mean the scheduler blocks while waiting for each exchange response. With Kafka:
1. **Decoupling**: The scheduler sends messages and moves on. Downloads happen independently.
2. **Backpressure**: If Binance is slow, messages queue up instead of timing out.
3. **Partition ordering**: The message key is the trading pair, so all BTCUSDT requests go to the same partition — guaranteeing order per pair.
4. **Error isolation**: A failed download for ETHUSDT doesn't block BTCUSDT downloads.

---

## 3. Database Decisions

### Q: Why MongoDB for candle data but MySQL for users?

**MySQL for auth** — user data is relational. Users have roles (`ENUM`), pairs have base/quote assets, intervals have descriptions. Schema enforcement prevents invalid data. Flyway migrations provide version-controlled schema changes.

**MongoDB for market** — candle data is high-volume, append-heavy, and self-contained. Each candle document contains all its data (no JOINs needed). The compound unique index `{symbol, intervalCode, openTime, exchange}` handles both deduplication and efficient queries. MongoDB scales horizontally for write-heavy workloads.

### Q: How do you handle duplicate candle data?

Two layers of protection:

1. **Application-level check**: `CandleDataPersistenceService` calls `existsBySymbolAndIntervalCodeAndOpenTimeAndExchange()` before each save. If the candle already exists, it skips it.
2. **Database-level constraint**: MongoDB compound unique index on `{symbol, intervalCode, openTime, exchange}`. Even if the application check is bypassed (race condition), MongoDB rejects the duplicate.

### Q: How do you handle schema migrations?

Flyway handles MySQL schema migrations in auth. Migration files are versioned (`V1__init_schema.sql`, `V2__seed_pairs_and_intervals.sql`, `V3__seed_admin_user.sql`) and run automatically on startup. `spring.jpa.hibernate.ddl-auto=validate` ensures Hibernate validates the schema but never modifies it — all changes go through Flyway.

MongoDB doesn't use migrations because it's schema-flexible. The application defines the document structure via `@Document` annotations.

---

## 4. Caching

### Q: How does caching work in your system?

Redis caching with Spring's `@Cacheable` / `@CacheEvict` annotations:

- **`candles` cache** (60s TTL) — caches `CandleQueryService.getCandles()` results. Key includes all query parameters (`pair:interval:start:end:limit:exchange`). Evicted when new candles are persisted.
- **`schedulerConfig` cache** (30s TTL) — caches the pair/interval config fetched from auth service. Prevents hitting auth on every candle query.

Serialization uses `GenericJackson2JsonRedisSerializer` with `activateDefaultTyping` so Redis stores Java type information alongside the JSON, enabling correct deserialization back to domain objects.

### Q: How do you invalidate the cache?

`@CacheEvict(value="candles", allEntries=true)` on `CandleDataPersistenceService.persistCandles()`. When new candles are saved, the entire candle cache is cleared. The next query hits MongoDB directly and the result is re-cached.

This is a coarse-grained invalidation strategy — simple but effective. A more targeted approach would evict only the specific pair/interval, but the 60-second TTL makes this unnecessary.

---

## 5. Security

### Q: How does JWT authentication work across services?

1. Client sends `POST /api/v1/auth/login` with credentials
2. Auth service validates credentials, creates JWT with username + roles, returns access + refresh tokens
3. Client sends requests to market/technical with `Authorization: Bearer <token>`
4. `JwtAuthenticationFilter` extracts the token, calls auth service via gRPC (`ValidateToken` RPC)
5. Auth service validates the JWT (signature, expiration) and returns `ValidateTokenResponse(valid, username, roles)`
6. Filter creates a `UsernamePasswordAuthenticationToken` and sets it in `SecurityContextHolder`
7. Spring Security's `authorizeHttpRequests` rules check roles against endpoint requirements

### Q: Why validate JWT via gRPC instead of locally?

Centralizing validation in auth means:
- Market and technical never see the JWT secret — no secret distribution problem
- Token revocation can be added in one place (e.g., a blacklist in Redis)
- Token format changes only affect auth service
- Trade-off: extra network hop per request, mitigated by gRPC's low latency (~1-2ms on localhost)

### Q: How do you handle service-to-service authentication?

Two mechanisms:
1. **gRPC** — market/technical → auth for JWT validation. No additional auth on the gRPC channel itself (plaintext, trusted internal network).
2. **Internal API key** — market → auth REST API for scheduler config. Uses `X-API-Key` header validated by `InternalApiKeyFilter` in auth. This is simpler than JWT for server-to-server calls that don't represent a user.

### Q: What role-based access control do you have?

| Role | Access |
|---|---|
| `ROLE_USER` | Candle queries, cache refresh, indicators, scanner, strategy, backtest |
| `ROLE_ADMIN` | All above + download/backfill endpoints |

Admin-only endpoints use both URL-level security (`hasRole("ADMIN")` in `SecurityConfig`) and method-level security (`@PreAuthorize("hasRole('ADMIN')")` on `DownloadController`).

---

## 6. gRPC

### Q: Why gRPC instead of REST for internal communication?

| | gRPC | REST |
|---|---|---|
| **Serialization** | Protobuf (binary, compact) | JSON (text, larger) |
| **Contract** | `.proto` file (strongly typed, code generation) | OpenAPI/docs (loosely typed) |
| **Performance** | HTTP/2, multiplexing, header compression | HTTP/1.1 per-request |
| **Streaming** | Native bidirectional streaming | Not standard |

For internal service-to-service calls happening on every request (JWT validation), gRPC's lower latency and binary format make a meaningful difference.

### Q: How do you define gRPC contracts?

Protobuf `.proto` files in the shared `candilize-proto` module:

- `auth.proto` — defines `AuthService` with `ValidateToken` and `GetUserByUsername` RPCs
- `market.proto` — defines `MarketService` with `GetCandles` RPC

Maven's `protobuf-maven-plugin` generates Java stubs at build time. All modules depend on `candilize-proto`, so they all get type-safe generated code.

### Q: How do you monitor gRPC calls?

Custom interceptors using Spring gRPC's `@GlobalServerInterceptor` / `@GlobalClientInterceptor`:
- **Logging interceptors** — log method name, status code, and duration for every call
- **Metrics interceptors** — record Micrometer counters (`grpc.server.calls`) and timers (`grpc.server.call.duration`) tagged by method and status code

Plus built-in gRPC health checks and reflection for service discovery via `grpcurl`.

---

## 7. Kafka

### Q: Walk me through the Kafka-based download pipeline.

1. `PriceDownloadScheduler` fires on cron (separate schedule per interval — 1m, 5m, 1h, etc.)
2. Fetches enabled pairs/intervals from auth service via REST
3. For each enabled pair, sends a `KafkaPriceRequest` to the `get-price` topic (key = pair name)
4. `KafkaConsumerService` receives the message
5. `CandleDownloadService.downloadAndPersist()`:
   - `CandleDataProviderFactory` selects the right exchange provider
   - Provider calls exchange API via `WebClient`
   - Adapter converts exchange-specific response to `Ohlcv` domain model
   - `CandleDataPersistenceService` saves to MongoDB (skips duplicates)
   - `@CacheEvict` clears Redis candle cache

### Q: How do you handle Kafka deserialization errors?

`ErrorHandlingDeserializer` wraps `JacksonJsonDeserializer` in the consumer config. If a message has malformed JSON, the `ErrorHandlingDeserializer` catches the exception and produces a `null` value instead of crashing the consumer. This prevents one bad message from blocking the entire partition.

### Q: How do you handle download failures?

`@Retryable` on `CandleDownloadService.downloadAndPersist()` — retries up to 3 times with 2-second backoff for any exception. If all retries fail, the error is logged but not re-thrown by `KafkaConsumerService`, preventing consumer reprocessing loops.

---

## 8. Testing & Quality

### Q: How do you test without hitting exchange APIs?

`TestCandleDataProvider` — activated by `app.testing-mode=true`. Generates synthetic OHLCV data with realistic price movements and correct time alignment. The `CandleDataProviderFactory` returns this provider instead of real exchange providers when testing mode is enabled.

### Q: What quality tools do you use?

- **JaCoCo 0.8.12** — code coverage reports
- **SonarQube** — static analysis for vulnerabilities, code smells, and quality gate enforcement (`sonar.qualitygate.wait=true` — build fails if gate not passed)
- **Spring Boot Test + Spring Security Test** — integration testing with security context

---

## 9. Error Handling

### Q: How do you handle errors in your REST APIs?

Centralized `GlobalExceptionHandler` with `@ControllerAdvice` in both auth and market:

| Exception | HTTP Status | When |
|---|---|---|
| `EntityNotFoundException` | 404 | Pair/interval not found or disabled |
| `UnsupportedExchangeException` | 400 | Unknown exchange name |
| `IllegalArgumentException` | 400 | Invalid interval code |
| `MethodArgumentNotValidException` | 400 | Bean validation fails (`@Valid`) |
| `Exception` (catch-all) | 500 | Unexpected errors |

All errors return a consistent `ErrorResponse`:
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Pair not found or disabled: INVALIDPAIR",
  "path": "/api/v1/candles/INVALIDPAIR/1h",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

### Q: How do gRPC errors differ from REST errors?

In the `MarketGrpcService`, errors use gRPC status codes:
```java
responseObserver.onError(
    Status.INVALID_ARGUMENT
        .withDescription("Invalid interval code: " + request.getIntervalCode())
        .asRuntimeException());
```

In `AuthGrpcService`, validation failures return a response body with `valid=false` and `error_message` instead of throwing — the caller checks the boolean.

---

## 10. Spring-Specific Questions

### Q: Explain the difference between `@Cacheable` and `@CacheEvict`.

- `@Cacheable("candles")` on `getCandles()` — before running the method, Spring checks Redis. If the cache has a matching key, it returns the cached value and skips the method entirely. If not, it runs the method and caches the result.
- `@CacheEvict(value="candles", allEntries=true)` on `persistCandles()` — after the method runs, Spring removes all entries from the `candles` cache.

### Q: What does `@ConditionalOnProperty` do?

`@ConditionalOnProperty(name="app.scheduler.enabled", havingValue="true")` on `PriceDownloadScheduler` — Spring only creates this bean if the property is set to `true`. This prevents the scheduler from running in test environments or in the technical service.

### Q: What's the difference between `MongoRepository` and `MongoTemplate`?

- `MongoRepository` — Spring Data generates query implementations from method names (e.g., `findBySymbolAndIntervalCode...`). Great for standard CRUD and derived queries.
- `MongoTemplate` — lower-level API for queries that can't be expressed as method names. Used in `CandleDataMongoRepositoryImpl` for `findDistinct("intervalCode")`.

### Q: How does Spring DI work with the factory pattern?

In `CandleDataProviderFactory`, the constructor takes `List<CandleDataProvider> providers`. Spring automatically injects ALL beans that implement `CandleDataProvider` — Binance, MEXC, and Test providers. The factory builds a map from this list, keyed by exchange code. No manual bean registration needed.

---

## 11. Data Flow Questions

### Q: Trace a candle query from HTTP request to response.

```
1. GET /api/v1/candles/BTCUSDT/1h?limit=100 (with Bearer token)
2. JwtAuthenticationFilter extracts JWT from Authorization header
3. AuthGrpcClient.validateToken(jwt) → gRPC call to auth:9090
4. Auth validates JWT, returns ValidateTokenResponse(valid=true, username="user1", roles=["ROLE_USER"])
5. Filter sets SecurityContextHolder.authentication
6. Spring Security permits (endpoint requires .authenticated())
7. CandleController.getCandles() delegates to CandleQueryService
8. @Cacheable checks Redis "candles" cache → cache miss
9. ConfigValidationService.validatePair("BTCUSDT") → checks against scheduler config (cached 30s)
10. ConfigValidationService.validateInterval("1h") → same check
11. CandleDataMongoRepository.findBySymbol...() → MongoDB query
12. Documents mapped to CandleResponse DTOs
13. Result cached in Redis (60s TTL)
14. JSON response returned to client
```

### Q: How does the scheduler trigger downloads?

```
1. PriceDownloadScheduler.schedule1h() fires (cron: "0 0 * * * *")
2. @ConditionalOnProperty ensures scheduler is only active when enabled
3. schedulerConfigClient.fetchSchedulerConfig() → REST call to auth (/api/v1/internal/scheduler-config)
   - Uses X-API-Key header (not JWT — this is service-to-service)
   - Response cached in Redis (30s) via ConfigValidationService
4. Checks if "1h" interval is in the enabled intervals list
5. For each enabled pair (e.g., BTCUSDT, ETHUSDT):
   - Builds KafkaPriceRequest(pair=BTCUSDT, interval=1h, limit=1, exchange=binance)
   - KafkaProducerService sends to "get-price" topic (key=BTCUSDT)
6. KafkaConsumerService receives message
7. CandleDownloadService:
   - CandleDataProviderFactory.getProvider("binance") → BinanceCandleDataProvider
   - Provider calls Binance REST API via WebClient
   - BinanceKlineToOhlcvAdapter converts response to Ohlcv domain objects
   - CandleDataPersistenceService checks for duplicates, saves new candles to MongoDB
   - @CacheEvict clears Redis candle cache
```

---

## 12. Scalability & Production Questions

### Q: How would you scale this system?

- **Auth**: Horizontal scaling behind a load balancer. JWT validation is stateless (just needs the signing key). Redis shared across instances.
- **Market**: Scale consumers by adding more instances to the Kafka consumer group — Kafka rebalances partitions automatically. MongoDB can shard by symbol.
- **Technical**: Fully stateless — scale horizontally without any coordination.

### Q: What would you change for production?

1. **CORS**: Change `setAllowedOrigins(List.of("*"))` to specific origins
2. **JWT secret**: Use a proper key management service instead of environment variable
3. **gRPC TLS**: Switch from `plaintext` to TLS for gRPC channels
4. **Rate limiting**: Add rate limiting on REST endpoints (especially candle queries)
5. **Circuit breaker**: Add Resilience4j circuit breaker for exchange API calls
6. **Monitoring**: Integrate Prometheus + Grafana for metrics dashboards
7. **Service discovery**: Replace hardcoded `static://localhost:9090` with service discovery (Consul, Eureka, K8s DNS)
8. **Secrets management**: Use Vault or K8s secrets instead of `.properties` defaults

### Q: What are the current limitations?

- gRPC validation adds a network hop per request (trade-off for centralized auth)
- `@CacheEvict(allEntries=true)` is coarse — clears all candle cache, not just the affected pair
- No circuit breaker on exchange API calls (only retry)
- Scheduler cron expressions are fixed — can't dynamically change without restart
- Technical module services are stubs — indicators, scanner, strategy, and backtest are not yet implemented
