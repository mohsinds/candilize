# Candilize

Candilize is a **crypto pricing data download and serving system** built with Spring Boot. It fetches OHLCV (candle) data from multiple exchanges (Binance, MEXC), stores **pricing data in MongoDB** and **users, pairs, and intervals in MySQL**, exposes it via REST APIs with JWT authentication, and uses Kafka for asynchronous download pipelines and Redis for caching.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Architecture Overview](#architecture-overview)
- [Project Structure & Layers](#project-structure--layers)
- [Prerequisites & Setup](#prerequisites--setup)
- [Configuration](#configuration)
- [API Documentation](#api-documentation)
- [Controller Advice & AOP Logging](#controller-advice--aop-logging)
- [Testing the Full Flow](#testing-the-full-flow)
- [License](#license)

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Runtime** | Java 17, Spring Boot 4.0.2 |
| **Web** | Spring Web (REST), Spring WebFlux (exchange API clients) |
| **Security** | Spring Security, JWT (jjwt 0.12.6), BCrypt |
| **Data** | Spring Data JPA + MySQL (users, pairs, intervals), Spring Data MongoDB (candle data), Flyway |
| **Cache** | Spring Data Redis |
| **Messaging** | Spring Kafka |
| **Validation** | Jakarta Bean Validation |
| **Resilience** | Spring Retry |
| **AOP** | Spring AOP (logging aspect) |

---

## Architecture Overview

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                     Client (REST)                         │
                    └────────────────────────────┬────────────────────────────┘
                                                 │
                    ┌────────────────────────────▼────────────────────────────┐
                    │  Security: JWT Filter → AuthEntryPoint (401)             │
                    └────────────────────────────┬────────────────────────────┘
                                                 │
                    ┌────────────────────────────▼────────────────────────────┐
                    │  Controllers (Auth, Candle, Config, Download, Cache)    │
                    │  + GlobalExceptionHandler (Controller Advice)           │
                    │  + LoggingAspect (AOP: entry/exit/duration)             │
                    └───┬──────────────────┬──────────────────┬──────────────┘
                        │                  │                  │
        ┌───────────────▼──────┐  ┌────────▼────────┐  ┌─────▼─────────────────┐
        │  AuthService         │  │ CandleQuerySvc  │  │ KafkaProducerService  │
        │  ConfigService      │  │ ConfigService   │  │ (get-price topic)     │
        └───────────────┬──────┘  └────────┬────────┘  └─────┬─────────────────┘
                        │                  │                  │
        ┌───────────────▼──────┐  ┌────────▼────────┐        │
        │  UserRepository      │  │ CandleDataMongo │        │
        │  (MySQL)             │  │ Repo + Redis   │        │
        └─────────────────────┘  └─────────────────┘        │
                                                              │
                    ┌─────────────────────────────────────────▼─────────────────┐
                    │  Kafka: get-price topic                                    │
                    └─────────────────────────────────────────┬─────────────────┘
                                                              │
                    ┌─────────────────────────────────────────▼─────────────────┐
                    │  KafkaConsumerService → CandleDownloadService            │
                    │  → CandleDataProviderFactory → Provider (Binance/MEXC/   │
                    │    Test) → CandleDataPersistenceService → MongoDB        │
                    └──────────────────────────────────────────────────────────┘
```

- **Request flow (read path):** Client → JWT filter → Controller → Service → DB (or Redis cache) → Response.
- **Download flow:** Scheduler or Download/Cache controller → Kafka producer → get-price topic → Consumer → Provider → Persist to DB → Cache eviction when needed.

---

## Project Structure & Layers

```
src/main/java/com/mohsindev/candilize/
├── CandilizeApplication.java          # Entry point; enables caching, retry, config scan
├── aspect/
│   └── LoggingAspect.java             # AOP: logs controller/service/security method entry, args (sanitized), duration, exceptions
├── api/
│   ├── CandleController.java          # GET candles by pair/interval; GET intervals for pair (authenticated)
│   ├── CacheController.java           # GET refresh: trigger Kafka download (authenticated)
│   ├── IndicatorController.java      # Indicator endpoints (existing)
│   ├── controller/
│   │   ├── AuthController.java        # POST register, login, refresh (public)
│   │   ├── ConfigController.java      # CRUD pairs/intervals, list exchanges (ADMIN)
│   │   └── DownloadController.java   # POST download, backfill (ADMIN)
│   ├── dto/
│   │   ├── request/                   # RegisterRequest, LoginRequest, PairRequest, DownloadRequest
│   │   └── response/                  # AuthResponse, CandleResponse, PairResponse, IntervalResponse, ErrorResponse
│   └── exception/
│       ├── GlobalExceptionHandler.java  # @RestControllerAdvice: maps exceptions → ErrorResponse + status
│       ├── EntityNotFoundException.java
│       └── UnsupportedExchangeException.java
├── configuration/
│   ├── AspectConfig.java              # @EnableAspectJAutoProxy for LoggingAspect
│   ├── CacheConfig.java               # Redis CacheManager, TTLs for candles / config caches
│   ├── ExchangeProperties.java       # exchange.mexc.base-url, exchange.binance.base-url, defaultExchange
│   ├── KafkaAdminConfig.java
│   ├── KafkaConsumerConfig.java
│   ├── KafkaProducerConfig.java
│   ├── KafkaTopicConfig.java
│   ├── SchedulerConfig.java           # @EnableScheduling when app.scheduler.enabled=true
│   └── WebClientConfig.java           # WebClient beans for MEXC/Binance
├── domain/
│   ├── KafkaPriceRequest.java        # Payload for get-price topic (requestId, priceObject, timestamp)
│   └── Ohlcv.java                    # Domain candle: interval, timestamp, open, high, low, close, volume
├── enums/
│   └── CandleInterval.java           # 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 1d, 1w, 1M
├── infrastructure/
│   ├── enums/
│   │   └── ExchangeName.java         # MEXC, BINANCE, TEST
│   ├── market/
│   │   ├── CandleDataProvider.java   # Strategy: getCandles(pair, interval, limit)
│   │   ├── CandleDataProviderFactory.java  # Resolves provider by exchange; returns TestCandleDataProvider when testing-mode
│   │   ├── TestCandleDataProvider.java     # Dummy data when app.testing-mode=true
│   │   ├── KlineToOhlcvAdapter.java  # Adapter: exchange kline DTO → Ohlcv
│   │   ├── binance/                  # BinanceCandleDataProvider, BinanceDataClient, BinanceKlineToOhlcvAdapter
│   │   └── mexc/                     # MexcCandleDataProvider, MexcDataClient, MexcKlineToOhlcvAdapter
│   └── persistence/
│       ├── entity/                   # UserEntity, SupportedPairEntity, SupportedIntervalEntity (MySQL/JPA)
│       ├── document/                 # CandleDataDocument (MongoDB)
│       └── repository/               # JPA repos (User, Pair, Interval); CandleDataMongoRepository + custom impl (MongoDB)
├── security/
│   ├── AuthEntryPoint.java           # Returns 401 JSON when unauthenticated
│   ├── JwtAuthenticationFilter.java # Extracts JWT, validates, sets SecurityContext
│   ├── JwtTokenProvider.java         # Generate/validate JWT access & refresh tokens
│   ├── SecurityConfig.java          # Filter chain, CORS, BCrypt, role-based access
│   └── UserDetailsServiceImpl.java  # Loads user from DB for Spring Security
└── service/
    ├── AuthService.java              # Register, login, refreshToken
    ├── CandleDataPersistenceService.java  # Persist Ohlcv list to MongoDB candle_data (upsert by exists-check)
    ├── CandleDownloadService.java    # @Retryable: get provider → getCandles → persist
    ├── CandleQueryService.java       # getCandles (from DB + cache), getAvailableIntervalsForPair
    ├── ConfigService.java            # CRUD pairs/intervals; getEnabledPairs/getEnabledIntervals for scheduler
    ├── KafkaConsumerService.java     # @KafkaListener get-price → CandleDownloadService
    ├── KafkaProducerService.java     # sendProducerRequest(KafkaPriceRequest)
    ├── PriceDownloadScheduler.java    # @Scheduled per interval (1m, 5m, …): publish KafkaPriceRequest for enabled pairs
    └── ...
```

---

## Prerequisites & Setup

- **Java 17**
- **MySQL** (e.g. 8.x) — database `candilize` for users, supported_pairs, supported_intervals (created automatically if `createDatabaseIfNotExist=true`)
- **MongoDB** — database `candilize` for candle (pricing) data
- **Redis** — for candle and config caches
- **Kafka** — for the `get-price` topic (e.g. single broker for dev)

Optional environment variables (with defaults in `application.properties`):

- `DB_PASSWORD` — MySQL password (default: `root`)
- `MONGODB_URI` — MongoDB connection URI (default: `mongodb://localhost:27017/candilize`)
- `JWT_SECRET` — Base64-encoded secret for JWT signing (default: dev-only value)

1. Start MySQL, MongoDB, Redis, and Kafka.
2. Run Flyway migrations (on first boot): MySQL tables `users`, `supported_pairs`, `supported_intervals` are created and seeded; candle data is stored in MongoDB only.
3. Start the application:

```bash
./mvnw spring-boot:run
```

Server runs at `http://localhost:8080`. Actuator health: `http://localhost:8080/actuator/health`.

---

## Configuration

Key properties (see `src/main/resources/application.properties`):

| Property | Description |
|----------|-------------|
| `spring.datasource.*` | MySQL URL, username, password (use `DB_PASSWORD` in prod); used for users, pairs, intervals |
| `spring.data.mongodb.uri` | MongoDB URI (use `MONGODB_URI` in prod); used for candle/pricing data only |
| `spring.data.redis.*` | Redis host, port, timeout |
| `app.jwt.secret`, `app.jwt.access-token-expiration`, `app.jwt.refresh-token-expiration` | JWT signing and expiry |
| `app.cache.candle-ttl`, `app.cache.config-ttl` | Redis TTL in seconds for candle and config caches |
| `app.scheduler.enabled` | Enable/disable scheduled downloads |
| `app.scheduler.cron.*` | Cron expressions per interval (1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w, 1M) |
| `app.testing-mode` | If `true`, use TestCandleDataProvider (dummy data) for all exchanges |
| `exchange.default-exchange` | Default exchange (e.g. `binance`) |
| `app.kafka.topic.price-request` | Kafka topic name (e.g. `get-price`) |

---

## API Documentation

Base path: `/api/v1`. All endpoints except auth and health require `Authorization: Bearer <accessToken>` unless stated otherwise.

### Auth (public)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Register: body `{ "username", "email", "password" }`. Validation: email format, password min 8 chars. |
| POST | `/auth/login` | Login: body `{ "username", "password" }`. Returns `{ "accessToken", "refreshToken", "tokenType", "expiresIn" }`. |
| POST | `/auth/refresh` | Body `{ "refreshToken": "..." }`. Returns new access and refresh tokens. |

### Candles (authenticated)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/candles/{pair}/{interval}` | Candles for pair/interval. Query: `limit` (default 100), `startTime`, `endTime` (epoch ms), `exchange` (optional). Data from DB; cache key includes params. |
| GET | `/candles/{pair}` | List of interval codes that have stored data for this pair. |

### Config (ADMIN)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/config/pairs` | List all supported pairs. |
| POST | `/config/pairs` | Add pair: body `{ "symbol", "baseAsset", "quoteAsset" }`. |
| PUT | `/config/pairs/{id}` | Update pair: body `{ "enabled": true/false }`. |
| DELETE | `/config/pairs/{id}` | Delete pair. |
| GET | `/config/intervals` | List all supported intervals. |
| PUT | `/config/intervals/{id}` | Update interval: body `{ "enabled": true/false }`. |
| GET | `/config/exchanges` | List available exchange codes. |

### Download (ADMIN)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/download` | Trigger download: body `{ "pair", "interval", "exchange", "limit" }`. Sends message to Kafka. |
| POST | `/download/backfill` | Backfill: same body; optional `startTime`, `endTime`. |

### Cache (authenticated)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/cache/refresh/{pair}/{interval}/{limit}` | Trigger async download for pair/interval/limit (default exchange). |

### Error responses

All errors go through `GlobalExceptionHandler` and return JSON like:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired JWT token",
  "path": "/api/v1/candles/BTCUSDT/1h",
  "timestamp": "2026-02-14T12:00:00Z"
}
```

---

## Controller Advice & AOP Logging

### GlobalExceptionHandler (@RestControllerAdvice)

- **Role:** Centralized exception handling for all REST controllers.
- **Behavior:** Catches `AuthenticationException`, `AccessDeniedException`, `UnsupportedExchangeException`, `EntityNotFoundException`, `MethodArgumentNotValidException`, `IllegalArgumentException`, and generic `Exception`.
- **Response:** Builds an `ErrorResponse` (status, error, message, path, timestamp) and returns the appropriate HTTP status (401, 403, 400, 404, 500).
- **Logging:** Logs warnings for auth/access failures and validation; debug for not-found/bad-request; error for unexpected exceptions.

### LoggingAspect (AOP)

- **Role:** Cross-cutting logging for controller, service, and security layers.
- **Pointcuts:**
  - `com.mohsindev.candilize.api..*` and `api.controller..*` (CONTROLLER)
  - `com.mohsindev.candilize.service..*` (SERVICE)
  - `com.mohsindev.candilize.security..*` (SECURITY)
- **Behavior:** For each method invocation:
  - Logs **entry** with layer, class name, method name, and **sanitized** arguments (passwords, tokens, etc. masked as `***`).
  - Runs the method and measures **duration**.
  - Logs **exit** with duration, or **exception** with duration and message.
- **Configuration:** `AspectConfig` enables `@EnableAspectJAutoProxy` so that `LoggingAspect` is applied. Use `logging.level.com.mohsindev.candilize=DEBUG` to see entry/exit logs.

---

## Testing the Full Flow

1. **Register a user**
   ```bash
   curl -s -X POST http://localhost:8080/api/v1/auth/register \
     -H "Content-Type: application/json" \
     -d '{"username":"user1","email":"user1@test.com","password":"password123"}'
   ```

2. **Login and get JWT**
   ```bash
   curl -s -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"user1","password":"password123"}'
   ```
   Use the returned `accessToken` in the next steps.

3. **Call protected endpoint (candles)**
   ```bash
   curl -s http://localhost:8080/api/v1/candles/BTCUSDT/1h?limit=10 \
     -H "Authorization: Bearer <accessToken>"
   ```

4. **Admin: use seeded admin user** (if V3 migration created it: username `admin`, password `admin123`) to call `/api/v1/config/pairs`, `/api/v1/download`, etc., with the admin’s JWT.

5. **Optional: testing mode** — set `app.testing-mode=true` to use dummy candle data from `TestCandleDataProvider` for all exchanges (no real API calls).

---

## License

See repository or project metadata.
