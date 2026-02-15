# Candilize

Candilize is a **crypto pricing data download and serving system** built as a microservices architecture with Spring Boot. It fetches OHLCV (candle) data from multiple exchanges (Binance, MEXC), stores **pricing data in MongoDB** and **users, pairs, and intervals in MySQL**, exposes REST APIs with JWT authentication, uses **Kafka** for asynchronous download pipelines, **Redis** for caching, and **gRPC** for service-to-service communication.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Module Overview](#module-overview)
- [Infrastructure & Data Flow](#infrastructure--data-flow)
- [Prerequisites](#prerequisites)
- [How to Run](#how-to-run)
- [Configuration](#configuration)
- [Inter-Service Communication](#inter-service-communication)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)

---

## Architecture Overview

### High-Level System Diagram

```
                                    ┌─────────────────────────────────────────────────────────────────────────────┐
                                    │                              CLIENTS                                        │
                                    │                    (Web, Mobile, External APIs)                             │
                                    └─────────────────────────────────────────────────────────────────────────────┘
                                                                  │
                                         REST (JWT)               │               REST (JWT)
                                         :8081                    │                    :8082
                                    ┌────▼────┐              ┌────▼────┐         ┌────▼────────────┐
                                    │  Auth   │              │ Market  │         │   Technical     │
                                    │  :8081  │              │  :8080  │         │     :8082       │
                                    │  gRPC   │              │  gRPC   │         │  gRPC clients   │
                                    │  :9090  │              │  :9091  │         │                 │
                                    └────┬────┘              └────┬────┘         └────┬────────────┘
                                         │                        │                   │
                              MySQL      │      REST (X-API-Key)  │      gRPC         │ gRPC
                              Redis      │◄───────────────────────┤                   │
                                         │                        │                   │
                                         │              ┌─────────▼─────────┐         │
                                         │              │     Kafka         │         │
                                         │              │  get-price topic  │         │
                                         │              └─────────┬─────────┘         │
                                         │                        │                   │
                                         │              MongoDB   │                   │
                                         │              Redis     │                   │
                                    ┌────┴────┐              ┌────▼────┐         ┌────▼────┐
                                    │  MySQL  │              │ MongoDB │         │  Auth   │
                                    │  Redis  │              │  Redis  │         │  Market │
                                    └─────────┘              └─────────┘         └─────────┘
```

### Microservices Communication Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    CANDILIZE MICROSERVICES                                                  │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                             │
│   ┌──────────────────┐         gRPC           ┌──────────────────┐         gRPC          ┌────────────────┐ │
│   │ candilize-auth   │ ◄───────────────────── │ candilize-market │ ◄──────────────────── │ candilize-     │ │
│   │                  │   ValidateToken        │                  │   GetCandles          │ technical      │ │
│   │ • JWT validation │                        │ • REST candles   │                       │ • Indicators   │ │
│   │ • User/pair/     │                        │ • Kafka producer │                       │ • Scanner      │ │
│   │   interval CRUD  │                        │ • Kafka consumer │                       │ • Backtest     │ │
│   │ • gRPC server    │                        │ • gRPC server    │                       │ • Strategy     │ │
│   └────────┬─────────┘                        └────────┬─────────┘                       └────────────────┘ │
│            │                                           │                                                    │
│            │ REST (X-API-Key)                          │                                                    │
│            │ /api/v1/internal/scheduler-config         │                                                    │
│            │◄──────────────────────────────────────────┤                                                    │
│            │   (SchedulerConfigClient)                 │                                                    │
│            │                                           │                                                    │
│   ┌────────▼─────────┐                        ┌────────▼───────────┐                                        │
│   │ MySQL            │                        │ Kafka              │                                        │
│   │ • users          │                        │ • get-price        │                                        │
│   │ • pairs          │                        │   (async download) │                                        │
│   │ • intervals      │                        └────────┬───────────┘                                        │
│   └──────────────────┘                                 │                                                    │
│   ┌──────────────────┐                        ┌────────▼─────────┐                                          │
│   │ Redis            │                        │ MongoDB          │                                          │
│   │ • config cache   │                        │ • candle_data    │                                          │
│   └──────────────────┘                        └──────────────────┘                                          │
│                                               ┌──────────────────┐                                          │
│                                               │ Redis            │                                          │
│                                               │ • candles cache  │                                          │
│                                               │ • schedulerConfig│                                          │
│                                               └──────────────────┘                                          │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Data Flow: Candle Download Pipeline

```
┌─────────────────┐     Cron      ┌───────────────────────┐     REST      ┌─────────────────┐
│ PriceDownload   │ ────────────► │ SchedulerConfigClient │ ────────────► │ Auth Service    │
│ Scheduler       │   (1m,5m…)    │ fetchSchedulerConfig  │  X-API-Key    │ /internal/      │
└────────┬────────┘               └───────────────────────┘               │ scheduler-config│
         │                                    │                           └─────────────────┘
         │ pairs + intervals                  │
         ▼                                    │
┌──────────────────────┐                      │
│ KafkaProducerService │                      │
│ sendProducerRequest  │                      │
└────────┬─────────────┘                      │
         │ Kafka (get-price)                  │
         ▼                                    │
┌──────────────────────┐                      │
│ KafkaConsumerService │                      │
│ consumePriceRequest  │                      │
└────────┬─────────────┘                      │
         │                                    │
         ▼                                    │
┌──────────────────────┐     WebFlux         ┌─────────────────┐
│ CandleDownloadService│ ──────────────────► │ Binance / MEXC  │
│ downloadAndPersist   │   (exchange APIs)   │ Exchange APIs   │
└────────┬─────────────┘                     └─────────────────┘
         │
         ▼
┌──────────────────────┐
│ CandleData           │
│ PersistenceService   │ ───► MongoDB (candle_data)
└──────────────────────┘
         │
         ▼
    Cache eviction (Redis candles cache)
```

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Runtime** | Java 17, Spring Boot 4.0.2 |
| **Build** | Maven (multi-module) |
| **Web** | Spring Web (REST), Spring WebFlux (exchange API clients) |
| **Security** | Spring Security, JWT (jjwt 0.12.6), BCrypt |
| **RPC** | gRPC (spring-grpc 1.0.2), Protobuf |
| **Data – SQL** | Spring Data JPA, MySQL 8, Flyway |
| **Data – NoSQL** | Spring Data MongoDB |
| **Cache** | Spring Data Redis |
| **Messaging** | Spring Kafka |
| **Validation** | Jakarta Bean Validation |
| **Resilience** | Spring Retry |
| **AOP** | Spring AspectJ (logging, retry) |
| **Exchanges** | Binance, MEXC (REST APIs) |

---

## Module Overview

| Module | Port | gRPC Port | Description |
|--------|------|-----------|-------------|
| **candilize-proto** | — | — | Shared Protobuf definitions (auth.proto, market.proto) |
| **candilize-auth** | 8081 | 9090 | Auth, user management, pairs/intervals config, JWT issuer |
| **candilize-market** | 8080 | 9091 | Candle data, Kafka download pipeline, exchange integrations |
| **candilize-technical** | 8082 | — | Technical analysis (indicators, scanner, strategy, backtest) |

---

## Infrastructure & Data Flow

### MySQL (candilize-auth)

- **Database:** `candilize1` (configurable)
- **Tables:** `users`, `supported_pairs`, `supported_intervals`
- **Migrations:** Flyway (`db/migration/V1__*.sql`, V2, V3)
- **Use:** User accounts, trading pairs, candle intervals configuration

### MongoDB (candilize-market)

- **Database:** `candilize` (configurable via `MONGODB_URI`)
- **Collection:** `candle_data` (OHLCV documents)
- **Index:** Compound unique index on `(symbol, intervalCode, openTime, exchange)`
- **Use:** Time-series candle data from exchanges

### Redis

| Service | Cache Names | TTL | Use |
|---------|-------------|-----|-----|
| candilize-auth | config | 300s | Pair/interval config |
| candilize-market | candles | 60s | Candle query results |
| candilize-market | schedulerConfig | 30s | Scheduler config from auth |

### Kafka

| Topic | Producer | Consumer | Payload | Use |
|-------|----------|----------|---------|-----|
| `get-price` | candilize-market | candilize-market | `KafkaPriceRequest` (pair, interval, limit, exchange) | Async candle download pipeline |

### gRPC

| Service | Port | RPCs | Callers |
|---------|------|------|---------|
| AuthService | 9090 | ValidateToken, GetUserByUsername | candilize-market, candilize-technical |
| MarketService | 9091 | GetCandles | candilize-technical |

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| **Java** | 17+ | OpenJDK or Oracle JDK |
| **Maven** | 3.8+ | Or use `./mvnw` |
| **MySQL** | 8.x | For auth service |
| **MongoDB** | 5.x+ | For candle data |
| **Redis** | 6.x+ | For caching |
| **Kafka** | 3.x+ | Single broker OK for dev |

### Optional Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_PASSWORD` | (empty) | MySQL password |
| `MONGODB_URI` | `mongodb://localhost:27071/candilize` | MongoDB connection |
| `JWT_SECRET` | (dev-only base64) | JWT signing secret |
| `INTERNAL_API_KEY` | `internal-dev-key` | Service-to-service API key |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | Auth REST base URL |

---

## How to Run

### 1. Start Infrastructure

```bash
# MySQL (create DB candilize1 if needed)
# MongoDB on 27071 (or update application.properties)
# Redis on 6379
# Kafka on 9092

# Example with Docker Compose (if available):
# docker-compose up -d mysql mongodb redis kafka
```

### 2. Build the Project

```bash
# From project root
./mvnw clean install -DskipTests
```

### 3. Run Services (in order)

**Terminal 1 – Auth (depends on MySQL, Redis):**
```bash
./mvnw -pl candilize-auth spring-boot:run
```

**Terminal 2 – Market (depends on Auth, MongoDB, Redis, Kafka):**
```bash
./mvnw -pl candilize-market spring-boot:run
```

**Terminal 3 – Technical (depends on Auth, Market):**
```bash
./mvnw -pl candilize-technical spring-boot:run
```

### 4. Health Checks

| Service | Health URL |
|---------|------------|
| Auth | http://localhost:8081/actuator/health |
| Market | http://localhost:8080/actuator/health |
| Technical | http://localhost:8082/actuator/health |

---

## Configuration

### Key Properties by Module

#### candilize-auth

| Property | Description |
|----------|-------------|
| `spring.datasource.*` | MySQL URL, user, password |
| `spring.data.redis.*` | Redis host/port |
| `app.jwt.secret` | JWT signing secret |
| `app.internal.api-key` | Internal API key for /api/v1/internal/* |
| `spring.grpc.server.port` | gRPC server port (9090) |

#### candilize-market

| Property | Description |
|----------|-------------|
| `spring.mongodb.uri` | MongoDB connection URI |
| `spring.data.redis.*` | Redis host/port |
| `app.auth-service.url` | Auth REST URL for scheduler config |
| `app.kafka.topic.price-request` | Kafka topic (get-price) |
| `app.scheduler.cron.*` | Cron expressions per interval |
| `app.cache.candle-ttl` | Redis candle cache TTL (seconds) |
| `exchange.*.base-url` | Binance/MEXC API URLs |
| `spring.grpc.server.port` | gRPC server port (9091) |

#### candilize-technical

| Property | Description |
|----------|-------------|
| `spring.grpc.client.channels.auth.address` | Auth gRPC address |
| `spring.grpc.client.channels.market.address` | Market gRPC address |
| `app.auth-service.url` | Auth REST URL (e.g. for login redirects) |

---

## Inter-Service Communication

### REST

| From | To | Endpoint | Auth | Use |
|------|-----|----------|------|-----|
| Client | Auth | `/api/v1/auth/*` | None (register/login) | Auth flows |
| Client | Auth | `/api/v1/config/*` | JWT | Pairs/intervals CRUD |
| Client | Market | `/api/v1/candles/*` | JWT | Candle queries |
| Client | Technical | `/api/v1/indicator/*`, etc. | JWT | Technical analysis |
| Market | Auth | `/api/v1/internal/scheduler-config` | X-API-Key | Scheduler config |

### gRPC

| From | To | RPC | Use |
|------|-----|-----|-----|
| Market | Auth | ValidateToken | JWT validation for REST requests |
| Technical | Auth | ValidateToken | JWT validation for REST requests |
| Technical | Market | GetCandles | Fetch candles for indicators/backtest |

---

## API Documentation

### Auth Service (8081)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | — | Register user |
| POST | `/api/v1/auth/login` | — | Login, returns JWT |
| POST | `/api/v1/auth/refresh` | — | Refresh tokens |
| GET | `/api/v1/config/pairs` | JWT (ADMIN) | List pairs |
| POST | `/api/v1/config/pairs` | JWT (ADMIN) | Add pair |
| GET | `/api/v1/internal/scheduler-config` | X-API-Key | Internal: enabled pairs/intervals |

### Market Service (8080)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/candles/{pair}/{interval}` | JWT | Candles (query: limit, startTime, endTime, exchange) |
| GET | `/api/v1/candles/{pair}` | JWT | Available intervals for pair |
| POST | `/api/v1/download` | JWT (ADMIN) | Trigger Kafka download |
| GET | `/api/v1/cache/refresh/{pair}/{interval}/{limit}` | JWT | Refresh cache + trigger download |

### Technical Service (8082)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/indicator/**` | JWT | Technical indicators |
| GET | `/api/v1/scanner/**` | JWT | Scanner endpoints |
| GET | `/api/v1/strategy/**` | JWT | Strategy signals |
| GET | `/api/v1/backtest/**` | JWT | Backtest results |

---

## Project Structure

```
Candilize/
├── candilize-proto/          # Shared Protobuf (auth, market)
│   └── src/main/proto/
│       ├── auth.proto
│       └── market.proto
├── candilize-auth/           # Auth + Config service
│   ├── api/controller/       # REST controllers
│   ├── grpc/                 # AuthGrpcService (ValidateToken, GetUserByUsername)
│   ├── security/             # JWT, InternalApiKeyFilter
│   ├── service/              # AuthService, ConfigService
│   └── infrastructure/persistence/  # JPA entities, repositories
├── candilize-market/         # Market data service
│   ├── api/                  # REST, DTOs, SchedulerConfigClient
│   ├── grpc/                 # MarketGrpcService, AuthGrpcClient
│   ├── service/              # CandleQuery, KafkaProducer/Consumer, Download
│   ├── configuration/        # Kafka, Redis, WebClient
│   └── infrastructure/       # CandleDataDocument, repositories, providers
├── candilize-technical/      # Technical analysis service
│   ├── api/controller/       # Indicator, Scanner, Strategy, Backtest
│   ├── grpc/                 # AuthGrpcClient, MarketGrpcClient
│   ├── indicator/            # IndicatorService
│   ├── scanner/              # ScannerService
│   └── backtest/             # BacktestService
└── docs/
    └── TROUBLESHOOTING.md
```

---

## Troubleshooting

See **[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)** for:

- Spring Boot 4 migration (AOP starter, ObjectMapper, Netty DNS)
- Build and Maven issues
- Infrastructure (MySQL, MongoDB, Redis, Kafka) connectivity

---

## License

See repository or project metadata.
