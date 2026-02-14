# Candilize - Crypto Pricing Data Download System

## Project Context

This is an existing Spring Boot 4.0.2 (Java 17) project called **Candilize** that already has:

- **Multi-exchange strategy pattern**: `CandleDataProvider` interface with `BinanceCandleDataProvider` and `MexcCandleDataProvider` implementations
- **Adapter pattern**: `KlineToOhlcvAdapter<T>` with Binance/MEXC adapters converting exchange DTOs to `Ohlcv` domain model
- **Kafka infrastructure**: Producer/Consumer with topic `get-price`, `KafkaPriceRequest` domain model, full config (3 partitions, 24h retention, JSON serialization)
- **WebClient-based HTTP clients**: `BinanceDataClient` and `MexcDataClient` using reactive `WebClient`
- **Existing endpoints**: `CandleController` at `/api/v1/candles`, `CacheController`, `IndicatorController`
- **Domain models**: `Ohlcv` (BigDecimal fields: open/high/low/close/volume + timestamp + interval), `KafkaPriceRequest`, `BinanceKline`, `MexcKline`
- **Enums**: `CandleInterval` (1m,3m,5m,15m,30m,1h,2h,4h,1d,1w), `ExchangeName` (MEXC, BINANCE)
- **Config**: `ExchangeProperties` with base URLs, `WebClientConfig` with named WebClient beans

**What's missing**: No database, no auth, no caching, no background scheduling, no testing mode, consumer logic is empty.

---

## Task

Extend this project to build a complete crypto pricing data download and serving system. Follow the existing architectural patterns (strategy, adapter, factory) already in place. Do NOT rewrite what already works — build on top of it.

---

## 1. MySQL Database Setup

### Dependencies
Add to `pom.xml`:
- `spring-boot-starter-data-jpa`
- `mysql-connector-j` (MySQL driver)
- `spring-boot-starter-security`
- `spring-boot-starter-data-redis`
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (io.jsonwebtoken, version 0.12.6)
- `spring-boot-starter-validation`

### Database Schema

Create Flyway or Liquibase migrations (prefer Flyway with `spring-boot-starter-flyway`). Create the following tables:

#### `users` table
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,  -- BCrypt hashed
    role ENUM('ROLE_USER', 'ROLE_ADMIN') NOT NULL DEFAULT 'ROLE_USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### `supported_pairs` table
```sql
CREATE TABLE supported_pairs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,   -- e.g., BTCUSDT
    base_asset VARCHAR(10) NOT NULL,      -- e.g., BTC
    quote_asset VARCHAR(10) NOT NULL,     -- e.g., USDT
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Seed with initial data: `BTCUSDT`, `ETHUSDT`, `SOLUSDT`, `XRPUSDT`, `ADAUSDT`.

#### `supported_intervals` table
```sql
CREATE TABLE supported_intervals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    interval_code VARCHAR(5) NOT NULL UNIQUE,  -- e.g., 1m, 5m, 15m
    description VARCHAR(50),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
```

Seed with: `1m`, `5m`, `15m`, `30m`, `1h`, `4h`, `1d`, `1w`, `1M`. All enabled by default.

#### `candle_data` table (for persisting downloaded OHLCV)
```sql
CREATE TABLE candle_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    interval_code VARCHAR(5) NOT NULL,
    open_time BIGINT NOT NULL,            -- epoch millis
    open_price DECIMAL(24,8) NOT NULL,
    high_price DECIMAL(24,8) NOT NULL,
    low_price DECIMAL(24,8) NOT NULL,
    close_price DECIMAL(24,8) NOT NULL,
    volume DECIMAL(24,8) NOT NULL,
    close_time BIGINT NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    UNIQUE KEY uq_candle (symbol, interval_code, open_time, exchange)
);

CREATE INDEX idx_candle_lookup ON candle_data(symbol, interval_code, open_time, exchange);
```

### JPA Entities & Repositories
- Create JPA entities for each table in package `com.mohsindev.candilize.infrastructure.persistance.entity`
- Create Spring Data JPA repositories in `com.mohsindev.candilize.infrastructure.persistance.repository`
- Use `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column` annotations
- The `CandleDataEntity` should have a composite unique constraint on (symbol, interval_code, open_time, exchange) — use `INSERT IGNORE` or `ON DUPLICATE KEY UPDATE` logic to avoid duplicates when re-downloading

---

## 2. Provider Factory Pattern

### Extend Existing Strategy Pattern

The project already has `CandleDataProvider` interface and implementations. Build a **factory** on top:

```
com.mohsindev.candilize.infrastructure.market/
├── CandleDataProvider.java          (existing interface)
├── CandleDataProviderFactory.java   (NEW - factory)
├── TestCandleDataProvider.java      (NEW - dummy data provider)
├── binance/                         (existing)
└── mexc/                            (existing)
```

#### `CandleDataProviderFactory`
- Inject all `CandleDataProvider` implementations (use `Map<String, CandleDataProvider>` via Spring's named bean injection or a custom registry)
- Method: `CandleDataProvider getProvider(String exchangeName)` — returns the correct provider
- When testing mode is enabled (`app.testing-mode=true`), ALWAYS return `TestCandleDataProvider` regardless of requested exchange
- Throw a clear exception (custom `UnsupportedExchangeException`) if exchange is not supported

#### `TestCandleDataProvider`
- Implements `CandleDataProvider`
- Generates realistic dummy OHLCV data (randomized around a base price per pair):
  - BTCUSDT: base ~42000, ETHUSDT: base ~2200, SOLUSDT: base ~100, etc.
  - Random walk: each candle's open = previous close, high/low within ±2% of open, volume random
- Annotate with `@Component` and `@Profile("test")` OR use `@ConditionalOnProperty(name = "app.testing-mode", havingValue = "true")`
- Should generate the correct number of candles for the requested interval and limit

---

## 3. Background Scheduling Service

### `PriceDownloadScheduler`
Package: `com.mohsindev.candilize.service`

This is the core background service that automatically downloads pricing data.

#### Behavior:
1. On application startup and then on a fixed schedule, it:
   - Reads all **enabled** pairs from `supported_pairs` table
   - Reads all **enabled** intervals from `supported_intervals` table
   - For each (pair, interval) combination, triggers a download via Kafka
2. Uses `@Scheduled` with configurable cron/fixed-rate expressions per interval:
   - `1m` candles: run every 1 minute
   - `5m` candles: run every 5 minutes
   - `15m` candles: run every 15 minutes
   - `30m` candles: run every 30 minutes
   - `1h` candles: run every 1 hour
   - `4h` candles: run every 4 hours
   - `1d` candles: run every 24 hours (at midnight UTC)
   - `1w` candles: run every week (Sunday midnight UTC)
   - `1M` candles: run on 1st of every month

   Use separate `@Scheduled` methods for each interval group, with cron expressions configurable via `application.properties`:
   ```properties
   app.scheduler.cron.1m=0 * * * * *
   app.scheduler.cron.5m=0 */5 * * * *
   app.scheduler.cron.15m=0 */15 * * * *
   app.scheduler.cron.30m=0 */30 * * * *
   app.scheduler.cron.1h=0 0 * * * *
   app.scheduler.cron.4h=0 0 */4 * * *
   app.scheduler.cron.1d=0 0 0 * * *
   app.scheduler.cron.1w=0 0 0 * * SUN
   app.scheduler.cron.1M=0 0 0 1 * *
   ```
3. Add `@EnableScheduling` to the main application class or a config class
4. Add a property `app.scheduler.enabled=true` to enable/disable the entire scheduler (`@ConditionalOnProperty`)
5. Each scheduled task should:
   - Query DB for enabled pairs
   - For each pair, publish a `KafkaPriceRequest` to the `get-price` topic with the pair, interval, exchange, and a reasonable limit (e.g., 1 for latest candle, or a small batch)
   - The existing `KafkaProducerService` handles publishing

### Kafka Consumer Enhancement

Update the existing `KafkaConsumerService` (currently empty) to:
1. Receive `KafkaPriceRequest` from the `get-price` topic
2. Use `CandleDataProviderFactory` to get the correct provider
3. Call the provider to fetch OHLCV data
4. Persist the data to `candle_data` table (upsert — skip duplicates)
5. Handle errors gracefully with logging (do NOT let one failed pair/interval crash the consumer)
6. Use `@Retryable` (from `spring-retry`) for transient API failures (max 3 retries, 2s backoff)

---

## 4. REST API Endpoints

All endpoints require JWT authentication (see Section 6). Prefix: `/api/v1`

### Auth Endpoints (`/api/v1/auth`) — Public (no auth required)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Register new user (username, email, password) |
| POST | `/auth/login` | Login, returns JWT access token + refresh token |
| POST | `/auth/refresh` | Refresh expired access token using refresh token |

#### Request/Response DTOs:
- `RegisterRequest`: username, email, password (validated: email format, password min 8 chars)
- `LoginRequest`: username, password
- `AuthResponse`: accessToken, refreshToken, tokenType ("Bearer"), expiresIn (seconds)

### Candle Endpoints (`/api/v1/candles`) — Authenticated

Refactor the existing `CandleController`:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/candles/{pair}/{interval}` | Get candles for pair/interval. Query params: `limit` (default 100), `startTime` (epoch ms), `endTime` (epoch ms), `exchange` (optional, uses default) |
| GET | `/candles/{pair}` | Get all available intervals for a pair |

- Validate that `pair` is in the enabled supported pairs
- Validate that `interval` is in the enabled supported intervals
- Return data from `candle_data` DB table, NOT by calling exchange API directly
- Apply Redis caching (see Section 5)

### Config Endpoints (`/api/v1/config`) — Admin only (ROLE_ADMIN)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/config/pairs` | List all supported pairs |
| POST | `/config/pairs` | Add a new supported pair |
| PUT | `/config/pairs/{id}` | Update pair (enable/disable) |
| DELETE | `/config/pairs/{id}` | Delete a pair |
| GET | `/config/intervals` | List all supported intervals |
| PUT | `/config/intervals/{id}` | Enable/disable an interval |
| GET | `/config/exchanges` | List available exchanges |

#### DTOs:
- `PairRequest`: symbol, baseAsset, quoteAsset
- `PairResponse`: id, symbol, baseAsset, quoteAsset, enabled
- `IntervalResponse`: id, intervalCode, description, enabled

### Manual Download Endpoint (`/api/v1/download`) — Admin only

| Method | Path | Description |
|--------|------|-------------|
| POST | `/download` | Manually trigger download for specific pair/interval/exchange |
| POST | `/download/backfill` | Backfill historical data for a pair (specify date range) |

---

## 5. Redis Caching

### Dependencies
Already added `spring-boot-starter-data-redis` above.

### Configuration
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000
app.cache.candle-ttl=60       # TTL in seconds for candle data cache
app.cache.config-ttl=300      # TTL in seconds for config cache
```

### Cache Strategy
- Use **cache-aside pattern**:
  1. On GET `/candles/{pair}/{interval}?limit=X&startTime=Y&endTime=Z`:
     - Build cache key: `candles:{pair}:{interval}:{startTime}:{endTime}:{limit}`
     - Check Redis first
     - If cache hit → return cached data
     - If cache miss → query MySQL → store in Redis with TTL → return data
  2. When new candle data is persisted (by consumer), **invalidate** relevant cache keys for that pair+interval
- Use Spring's `@Cacheable`, `@CacheEvict` annotations with a custom `RedisCacheManager` OR use `RedisTemplate` directly for fine-grained control
- Create a `CacheConfig` class with `RedisCacheManager` bean, configure TTLs per cache name
- Cache names: `candles`, `supportedPairs`, `supportedIntervals`
- Config data (pairs, intervals) should also be cached since the scheduler reads them frequently

### Cache Invalidation
- When admin updates pairs/intervals config → evict `supportedPairs` / `supportedIntervals` cache
- When new candle data is written → evict matching candle cache entries
- Use `@CacheEvict(value = "candles", key = "...")` on write operations

---

## 6. Security Layer (JWT Authentication & Authorization)

### Architecture
```
com.mohsindev.candilize.security/
├── JwtAuthenticationFilter.java      # OncePerRequestFilter - extracts & validates JWT
├── JwtTokenProvider.java             # JWT token generation, validation, parsing
├── SecurityConfig.java               # SecurityFilterChain configuration
├── UserDetailsServiceImpl.java       # Loads user from DB for Spring Security
└── AuthEntryPoint.java               # Handles 401 responses
```

### `SecurityConfig`
- Use `SecurityFilterChain` bean (NOT deprecated `WebSecurityConfigurerAdapter`)
- Configure:
  - `/api/v1/auth/**` → `permitAll()`
  - `/api/v1/config/**` → `hasRole('ADMIN')`
  - `/api/v1/download/**` → `hasRole('ADMIN')`
  - `/api/v1/candles/**` → `authenticated()`
  - `/api/v1/cache/**` → `authenticated()`
  - `/actuator/health` → `permitAll()`
  - Everything else → `authenticated()`
- Stateless session management (`SessionCreationPolicy.STATELESS`)
- Add `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
- Configure `BCryptPasswordEncoder` bean
- Disable CSRF (stateless API)
- Enable CORS with configurable origins

### `JwtTokenProvider`
- Properties:
  ```properties
  app.jwt.secret=<base64-encoded-256-bit-secret>
  app.jwt.access-token-expiration=900000       # 15 minutes in ms
  app.jwt.refresh-token-expiration=604800000    # 7 days in ms
  ```
- Methods:
  - `generateAccessToken(UserDetails)` → JWT string
  - `generateRefreshToken(UserDetails)` → JWT string
  - `validateToken(String token)` → boolean
  - `getUsernameFromToken(String token)` → String
  - `getExpirationFromToken(String token)` → Date
- Use HMAC-SHA256 signing (HS256) via `io.jsonwebtoken` library
- Include claims: `sub` (username), `roles`, `iat`, `exp`

### `JwtAuthenticationFilter`
- Extends `OncePerRequestFilter`
- Extracts token from `Authorization: Bearer <token>` header
- Validates token, loads `UserDetails`, sets `SecurityContextHolder` authentication
- If token is invalid/missing on protected routes → `AuthEntryPoint` returns 401

### Error Responses
All auth errors should return consistent JSON:
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired JWT token",
  "path": "/api/v1/candles/BTCUSDT/1h",
  "timestamp": "2026-02-14T12:00:00Z"
}
```

Create a `GlobalExceptionHandler` with `@RestControllerAdvice` to handle:
- `AuthenticationException` → 401
- `AccessDeniedException` → 403
- `UnsupportedExchangeException` → 400
- `EntityNotFoundException` → 404
- `MethodArgumentNotValidException` → 400 (validation errors)
- Generic `Exception` → 500

---

## 7. Testing Mode

### Configuration
```properties
app.testing-mode=false   # Set to true to use dummy data instead of real API calls
```

### Behavior when `app.testing-mode=true`:
1. `CandleDataProviderFactory` returns `TestCandleDataProvider` for ALL exchange requests
2. `TestCandleDataProvider` generates deterministic-ish dummy data:
   - Base prices: BTCUSDT=42000, ETHUSDT=2200, SOLUSDT=100, XRPUSDT=0.55, ADAUSDT=0.45
   - Each candle: open ± random 0.5%, high = max(open, close) + random 0.3%, low = min(open, close) - random 0.3%, volume = random 100-10000
   - Timestamps calculated correctly based on interval
3. The scheduler still runs on schedule, but fetches dummy data
4. Kafka pipeline still works end-to-end (producer → consumer → DB), just with fake data
5. Log a warning on startup: `"⚠ TESTING MODE ENABLED - Using dummy price data"`

---

## 8. Application Properties

Add/update `application.properties`:
```properties
# ===== Database =====
spring.datasource.url=jdbc:mysql://localhost:3306/candilize?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# ===== Redis =====
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000
app.cache.candle-ttl=60
app.cache.config-ttl=300

# ===== JWT =====
app.jwt.secret=${JWT_SECRET:dGhpcyBpcyBhIHNlY3JldCBrZXkgZm9yIGRldmVsb3BtZW50IG9ubHk=}
app.jwt.access-token-expiration=900000
app.jwt.refresh-token-expiration=604800000

# ===== Scheduler =====
app.scheduler.enabled=true
app.scheduler.cron.1m=0 * * * * *
app.scheduler.cron.5m=0 */5 * * * *
app.scheduler.cron.15m=0 */15 * * * *
app.scheduler.cron.30m=0 */30 * * * *
app.scheduler.cron.1h=0 0 * * * *
app.scheduler.cron.4h=0 0 */4 * * *
app.scheduler.cron.1d=0 0 0 * * *
app.scheduler.cron.1w=0 0 0 * * SUN
app.scheduler.cron.1M=0 0 0 1 * *

# ===== Testing =====
app.testing-mode=false

# ===== Exchange (existing) =====
exchange.default-exchange=binance
```

---

## 9. Package Structure (Final)

```
com.mohsindev.candilize/
├── CandilizeApplication.java
├── api/
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── CandleController.java        (refactored)
│   │   ├── ConfigController.java         (NEW)
│   │   ├── DownloadController.java       (NEW)
│   │   └── CacheController.java          (existing)
│   ├── dto/
│   │   ├── request/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── PairRequest.java
│   │   │   └── DownloadRequest.java
│   │   └── response/
│   │       ├── AuthResponse.java
│   │       ├── PairResponse.java
│   │       ├── IntervalResponse.java
│   │       ├── CandleResponse.java
│   │       └── ErrorResponse.java
│   └── exception/
│       ├── GlobalExceptionHandler.java
│       └── UnsupportedExchangeException.java
├── configuration/
│   ├── KafkaProducerConfig.java          (existing)
│   ├── KafkaConsumerConfig.java          (existing)
│   ├── KafkaAdminConfig.java             (existing)
│   ├── KafkaTopicConfig.java             (existing)
│   ├── ExchangeProperties.java           (existing)
│   ├── WebClientConfig.java              (existing)
│   ├── CacheConfig.java                  (NEW)
│   └── SchedulerConfig.java              (NEW)
├── domain/
│   ├── KafkaPriceRequest.java            (existing)
│   └── Ohlcv.java                        (existing)
├── enums/
│   └── CandleInterval.java              (existing)
├── infrastructure/
│   ├── enums/
│   │   ├── ExchangeName.java             (existing)
│   │   └── IndicatorName.java            (existing)
│   ├── indicator/                        (existing, untouched)
│   ├── market/
│   │   ├── CandleDataProvider.java       (existing)
│   │   ├── CandleDataProviderFactory.java (NEW)
│   │   ├── CandleDataService.java        (existing)
│   │   ├── TestCandleDataProvider.java    (NEW)
│   │   ├── KlineToOhlcvAdapter.java      (existing)
│   │   ├── binance/                      (existing, untouched)
│   │   └── mexc/                         (existing, untouched)
│   └── persistence/
│       ├── entity/
│       │   ├── UserEntity.java
│       │   ├── SupportedPairEntity.java
│       │   ├── SupportedIntervalEntity.java
│       │   └── CandleDataEntity.java
│       └── repository/
│           ├── UserRepository.java
│           ├── SupportedPairRepository.java
│           ├── SupportedIntervalRepository.java
│           └── CandleDataRepository.java
├── security/
│   ├── JwtAuthenticationFilter.java
│   ├── JwtTokenProvider.java
│   ├── SecurityConfig.java
│   ├── UserDetailsServiceImpl.java
│   └── AuthEntryPoint.java
└── service/
    ├── CandleService.java               (existing interface - extend)
    ├── CandleServiceImpl.java            (existing - refactor)
    ├── KafkaProducerService.java         (existing)
    ├── KafkaConsumerService.java         (existing - enhance)
    ├── AuthService.java                  (NEW)
    ├── ConfigService.java                (NEW)
    ├── PriceDownloadScheduler.java       (NEW)
    └── IndicatorService.java             (existing)
```

---

## 10. Implementation Order

Follow this order to avoid circular dependencies and build incrementally:

1. **Add Maven dependencies** (JPA, MySQL, Redis, Security, JWT, Flyway, Validation, Spring Retry)
2. **Flyway migrations** (create all tables + seed data)
3. **JPA entities & repositories**
4. **Security layer** (JWT, SecurityConfig, UserDetailsService, AuthController)
5. **CandleDataProviderFactory** + **TestCandleDataProvider**
6. **ConfigController** + **ConfigService** (CRUD for pairs/intervals)
7. **Enhance KafkaConsumerService** (fetch data → persist to DB)
8. **PriceDownloadScheduler** (background download service)
9. **Refactor CandleController** (read from DB, not API)
10. **Redis caching layer** (CacheConfig, @Cacheable on services)
11. **DownloadController** (manual download + backfill)
12. **GlobalExceptionHandler** (consistent error responses)
13. **Testing mode** (TestCandleDataProvider + conditional wiring)

---

## 11. Important Implementation Notes

- **Do NOT break existing functionality** — refactor incrementally
- **Use constructor injection** everywhere (no `@Autowired` on fields)
- **Use `@Transactional`** on service methods that write to DB
- **Use DTOs** for API input/output — never expose entities directly
- **Validate all input** with `@Valid` and Jakarta Bean Validation annotations
- **Use `@Slf4j`** (Lombok) for logging consistently
- **Thread safety**: the scheduler runs on a thread pool — ensure services are stateless or properly synchronized
- **Idempotent writes**: candle data downloads may overlap — use upsert logic to avoid duplicate insertion errors
- **Graceful error handling**: one failed pair/interval download should NOT stop others
- **Environment variables**: sensitive values (DB password, JWT secret) should use `${ENV_VAR:default}` pattern
- **Fix the typo**: existing package is `persistance` — rename to `persistence` (correct spelling) during this refactor
