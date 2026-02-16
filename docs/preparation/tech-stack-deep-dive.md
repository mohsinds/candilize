# Tech Stack Deep Dive

Why each technology was chosen, how it's used, and what problems it solves.

---

## Spring Boot 4.0.2

**Role**: Application framework for all three microservices.

**Why Spring Boot?**
- Auto-configuration eliminates boilerplate (embedded Tomcat, data source setup, security defaults)
- Starter dependencies bundle compatible library versions
- Production-ready features via Actuator (health checks, metrics, info)
- Mature ecosystem for every integration this project needs

**How Candilize uses it:**
- `spring-boot-starter-web` — REST controllers in all modules
- `spring-boot-starter-security` — JWT-based security filter chain
- `spring-boot-starter-data-jpa` — MySQL access in auth
- `spring-boot-starter-data-mongodb` — MongoDB access in market
- `spring-boot-starter-data-redis` — Redis caching in auth + market
- `spring-boot-starter-kafka` — Kafka producer/consumer in market
- `spring-boot-starter-webflux` — `WebClient` for non-blocking HTTP (exchange APIs)
- `spring-boot-starter-validation` — `@Valid`, `@NotBlank` on request DTOs
- `spring-boot-starter-actuator` — `/actuator/health`, `/actuator/metrics`, `/actuator/grpc`
- `spring-boot-starter-flyway` — database migration at startup
- `spring-boot-starter-aspectj` — AOP support for `@Retryable`

**Key configuration pattern**: `@ConfigurationProperties` binds `exchange.*` properties to a type-safe Java record (`ExchangeProperties`), avoiding scattered `@Value` annotations.

---

## Java 17

**Role**: Language runtime. Declared as `<java.version>17</java.version>` in parent POM.

**Java 17 features used in Candilize:**

| Feature | Where | Example |
|---|---|---|
| **Records** | DTOs, configs, gRPC results | `record CandleResponse(...)`, `record SchedulerConfigResponse(...)`, `record ValidateResult(boolean valid, String username, List<String> roles)` |
| **Sealed classes** (potential) | Enum patterns | `ExchangeName`, `CandleInterval` as exhaustive enums |
| **Text blocks** | Not observed | — |
| **Pattern matching** | Not observed | — |
| **`var`** | Local variables | Used in service loops |

**Why Java 17 over newer versions?** LTS release — long-term support, broad library compatibility, production-stable.

---

## Maven (Multi-Module)

**Role**: Build tool and dependency management.

**Module structure:**
```
candilize (parent POM, packaging=pom)
├── candilize-proto      → shared protobuf definitions
├── candilize-auth       → depends on proto
├── candilize-market     → depends on proto
└── candilize-technical  → depends on proto
```

**Key Maven features used:**

| Feature | How |
|---|---|
| **Parent POM** | `spring-boot-starter-parent:4.0.2` — manages all Spring dependency versions |
| **BOM import** | `spring-grpc-dependencies:1.0.2` — manages gRPC dependency versions |
| **`<modules>`** | Multi-module reactor build — `mvn package` builds all 4 modules in dependency order |
| **Profiles** | `netty-native-dns-macos` — auto-activates on macOS, adds `netty-resolver-dns-native-macos` |
| **os-maven-plugin** | Detects OS for platform-specific Netty classifier and protoc binary |
| **JaCoCo** | Code coverage reporting — `prepare-agent` + `report` phases |
| **SonarQube** | `sonar-maven-plugin:3.11.0.3922` with `sonar.qualitygate.wait=true` |

**Build order** (enforced by reactor): proto → auth → market → technical.

---

## Spring Security + JWT (jjwt 0.12.6)

**Role**: Authentication and authorization across all three services.

**Architecture:**
```
Client → [JWT in Authorization header]
       → JwtAuthenticationFilter (market/technical)
       → gRPC call to auth:9090 → AuthGrpcService.validateToken()
       → JwtService.validateToken() → extracts claims
       → returns ValidateTokenResponse(valid, username, roles)
       → SecurityContextHolder.setAuthentication()
```

**Token lifecycle:**
1. **Login**: `POST /api/v1/auth/login` → auth service issues access + refresh tokens
2. **Access token**: 15 minutes (`app.jwt.access-token-expiration=900000`)
3. **Refresh token**: 7 days (`app.jwt.refresh-token-expiration=604800000`)
4. **Validation**: Every request validated via gRPC (not local — centralized validation)

**Why gRPC for validation (not local)?**
- Single source of truth — auth service owns the JWT secret
- No secret distribution — market/technical never see the signing key
- Revocation possible — auth could add token blacklisting without changing clients

**Role-based access control:**

| Role | Endpoints |
|---|---|
| `ROLE_USER` | Candle queries, cache, indicators, scanner, strategy, backtest |
| `ROLE_ADMIN` | All above + download endpoints (`/api/v1/download/**`) |

**Internal API key**: Service-to-service calls (market → auth for scheduler config) use `X-API-Key` header, not JWT. Validated by `InternalApiKeyFilter` in auth.

---

## MySQL 8+

**Role**: Relational database for auth service (users, config).

**Schema (Flyway-managed):**

| Table | Purpose | Key Columns |
|---|---|---|
| `users` | User accounts | `username`, `email`, `password` (BCrypt), `role` (ENUM), `enabled` |
| `supported_pairs` | Trading pair config | `symbol` (BTCUSDT), `base_asset`, `quote_asset`, `enabled` |
| `supported_intervals` | Interval config | `interval_code` (1h), `description`, `enabled` |

**Flyway migrations:**
- `V1__init_schema.sql` — creates all three tables
- `V2__seed_pairs_and_intervals.sql` — seeds initial pairs (BTCUSDT, ETHUSDT, etc.) and intervals
- `V3__seed_admin_user.sql` — seeds default admin account

**Why MySQL (not MongoDB for everything)?**
- Auth data is relational — users have roles, pairs have base/quote assets
- Strict schema enforcement — `ENUM('ROLE_USER', 'ROLE_ADMIN')` prevents invalid roles
- ACID transactions for user creation
- Flyway provides version-controlled, repeatable migrations

**JPA config**: `hibernate.ddl-auto=validate` — Hibernate validates schema against entities but never modifies it. Schema changes go through Flyway only.

---

## MongoDB

**Role**: Document database for candle data in market service.

**Why MongoDB (not MySQL)?**
- **Volume**: Millions of candle documents (11 intervals x N pairs x historical depth)
- **Schema flexibility**: Each candle is a self-contained document — no JOINs needed
- **Compound indexes**: `{symbol, intervalCode, openTime, exchange}` unique index for fast lookups and deduplication
- **No relationships**: Candle data doesn't reference other entities

**Document structure** (`candle_data` collection):
```json
{
  "_id": "ObjectId",
  "symbol": "BTCUSDT",
  "intervalCode": "1h",
  "openTime": 1705305600000,
  "openPrice": "42150.50",
  "highPrice": "42300.00",
  "lowPrice": "42100.00",
  "closePrice": "42250.75",
  "volume": "1234.567",
  "closeTime": 1705309200000,
  "exchange": "binance"
}
```

**Repository pattern**: `MongoRepository` for derived queries + `MongoTemplate` for custom queries (`findDistinct`).

---

## Redis

**Role**: Caching layer in auth and market services.

**Cache definitions:**

| Cache | Module | TTL | Key Pattern | Cached Data |
|---|---|---|---|---|
| `candles` | market | 60s (configurable) | `pair:interval:start:end:limit:exchange` | `List<CandleResponse>` |
| `schedulerConfig` | market | 30s (fixed) | `'config'` | `SchedulerConfigResponse` |
| Config cache | auth | 300s | Various | Auth service config data |

**Serialization**: `GenericJackson2JsonRedisSerializer` with `activateDefaultTyping` — stores Java type info in JSON so Redis can deserialize back to the correct class. `JavaTimeModule` handles `Instant` and `BigDecimal`.

**Cache invalidation**: `@CacheEvict(value="candles", allEntries=true)` on `CandleDataPersistenceService.persistCandles()` — clears entire candle cache when new data is saved.

---

## Apache Kafka

**Role**: Asynchronous message queue for the candle download pipeline in market service.

**Topic**: `get-price`

**Flow:**
```
PriceDownloadScheduler (cron)
    → KafkaProducerService.send(KafkaPriceRequest)
    → [get-price topic]
    → KafkaConsumerService.consume()
    → CandleDownloadService.downloadAndPersist()
    → Exchange API → MongoDB
```

**Why Kafka (not synchronous)?**
- **Decoupling**: Scheduler doesn't wait for downloads to complete
- **Backpressure**: If exchange APIs are slow, messages queue up instead of overwhelming the system
- **Ordering**: Message key = trading pair → all requests for BTCUSDT go to the same partition (ordering guarantee)
- **Retry**: Consumer failures don't block other pairs

**Producer config:**
- Serializer: `JacksonJsonSerializer` for `KafkaPriceRequest`
- ACKS: `"all"` — all in-sync replicas acknowledge (no data loss)
- Retries: 3

**Consumer config:**
- Deserializer: `ErrorHandlingDeserializer` wrapping `JacksonJsonDeserializer` — bad messages don't crash the consumer
- Group ID: `candle-price-consumer-group`
- Offset reset: `earliest` — on first start, reads from beginning

---

## gRPC (Spring gRPC 1.0.2)

**Role**: Service-to-service RPC for JWT validation and candle data fetching.

**Services defined in `.proto` files:**

| Service | Server | Client(s) | RPCs |
|---|---|---|---|
| `AuthService` | auth (:9090) | market, technical | `ValidateToken`, `GetUserByUsername` |
| `MarketService` | market (:9091) | technical | `GetCandles` |

**Why gRPC (not REST for internal calls)?**
- **Binary protocol** (protobuf) — smaller payloads, faster serialization than JSON
- **Strongly typed contracts** — `.proto` files are the source of truth, shared via `candilize-proto` module
- **Code generation** — Java stubs auto-generated at build time
- **Streaming support** — not used yet, but available for future real-time candle feeds
- **Health + reflection** — built-in service discovery via `grpcurl`

**Observability (custom interceptors):**
- `GrpcServerLoggingInterceptor` — logs method, status code, duration for every server call
- `GrpcServerMetricsInterceptor` — `grpc.server.calls` counter + `grpc.server.call.duration` timer (Micrometer)
- `GrpcClientLoggingInterceptor` — logs outbound call method and duration
- `GrpcClientMetricsInterceptor` — `grpc.client.calls` counter + `grpc.client.call.duration` timer

---

## WebClient (Spring WebFlux)

**Role**: Non-blocking HTTP client for external API calls in market service.

**Three WebClient beans:**

| Bean Name | Base URL | Used By |
|---|---|---|
| `binanceWebClient` | `https://api.binance.com` | `BinanceDataClient` (kline data) |
| `mexWebClient` | `https://api.mexc.com` | `MexcDataClient` (kline data) |
| `authServiceWebClient` | `http://localhost:8081` | `SchedulerConfigClient` (config) |

**Why WebClient (not RestTemplate)?**
- `RestTemplate` is deprecated in Spring Boot 4.x
- WebClient supports reactive (non-blocking) and blocking modes
- Used in blocking mode (`.block()`) here, but ready for reactive if needed

---

## Flyway

**Role**: Database migration tool for MySQL schema in auth service.

**Migration files** (in `src/main/resources/db/migration/`):
1. `V1__init_schema.sql` — creates `users`, `supported_pairs`, `supported_intervals` tables
2. `V2__seed_pairs_and_intervals.sql` — seeds trading pairs and intervals
3. `V3__seed_admin_user.sql` — seeds default admin user

**Config**: `spring.jpa.hibernate.ddl-auto=validate` ensures Hibernate never modifies the schema. All changes go through numbered Flyway migrations.

---

## Swagger/OpenAPI 3 (springdoc 2.8.4)

**Role**: Auto-generated API documentation.

**Endpoints:**
- Auth: `http://localhost:8081/swagger-ui.html`
- Market: `http://localhost:8080/swagger-ui.html`

**Security**: Swagger UI endpoints are `permitAll()` in `SecurityConfig`. API calls through Swagger use the "Authorize" button to set a Bearer JWT.

---

## Micrometer + Actuator

**Role**: Metrics collection and operational endpoints.

**Exposed Actuator endpoints** (all modules):
- `/actuator/health` — application health with details
- `/actuator/info` — application info
- `/actuator/metrics` — all Micrometer metrics
- `/actuator/grpc` — custom endpoint showing registered gRPC services/channels

**Custom gRPC metrics:**

| Metric | Type | Tags |
|---|---|---|
| `grpc.server.calls` | Counter | `method`, `status` |
| `grpc.server.call.duration` | Timer | `method`, `status` |
| `grpc.client.calls` | Counter | `method`, `status` |
| `grpc.client.call.duration` | Timer | `method`, `status` |

---

## Lombok

**Role**: Compile-time code generation to reduce boilerplate.

**Annotations used across the project:**

| Annotation | What It Generates | Used On |
|---|---|---|
| `@Data` | getters, setters, `equals`, `hashCode`, `toString` | Entities, documents |
| `@Builder` | Builder pattern class | DTOs, domain models, Kafka requests |
| `@Value` | Immutable class (final fields, getters only) | `KafkaPriceRequest` |
| `@RequiredArgsConstructor` | Constructor for `final` fields | Services, controllers (DI) |
| `@NoArgsConstructor` / `@AllArgsConstructor` | Default / all-args constructors | JPA entities, MongoDB documents |
| `@Slf4j` | `private static final Logger log` | Services, filters, interceptors |
| `@Getter` | Getters only | Enums (`CandleInterval`, `ExchangeName`) |

---

## Spring Retry (2.0.12)

**Role**: Automatic retry with backoff for exchange API calls.

**Usage** (in `CandleDownloadService`):
```java
@Retryable(
    retryFor = {Exception.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000)
)
public int downloadAndPersist(PriceObject po) { ... }
```

**Why retry?** Exchange APIs (Binance, MEXC) have rate limits and intermittent failures. Retrying with 2-second backoff handles transient errors without crashing the download pipeline.

**Requires**: `spring-boot-starter-aspectj` for AOP proxy support.

---

## JaCoCo + SonarQube

**Role**: Code quality and coverage analysis.

- **JaCoCo 0.8.12**: Generates coverage reports at `target/site/jacoco/`
- **SonarQube**: Static analysis for security vulnerabilities and code smells
  - `sonar.qualitygate.wait=true` — Maven build fails if quality gate isn't passed
  - Project key: `candilize`

---

## Dependency Summary by Module

| Dependency | Auth | Market | Technical |
|---|---|---|---|
| spring-boot-starter-web | x | x | x |
| spring-boot-starter-security | x | x | x |
| spring-grpc-spring-boot-starter | x | x | x |
| spring-boot-starter-actuator | x | x | x |
| spring-boot-starter-validation | x | x | x |
| jackson-datatype-jsr310 | x | x | x |
| lombok | x | x | x |
| candilize-proto | x | x | x |
| spring-boot-starter-data-jpa | x | | |
| mysql-connector-j | x | | |
| spring-boot-starter-flyway | x | | |
| jjwt (api/impl/jackson) | x | | |
| spring-boot-starter-data-redis | x | x | |
| spring-boot-starter-data-mongodb | | x | |
| spring-boot-starter-kafka | | x | |
| spring-boot-starter-webflux | | x | |
| spring-retry | | x | |
| spring-boot-starter-aspectj | | x | |
| springdoc-openapi | x | x | |
