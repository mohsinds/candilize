# Candilize — Project Overview

## What Is Candilize?

Candilize is a **cryptocurrency candle data platform** built with a microservices architecture. It downloads, stores, and serves OHLCV (Open, High, Low, Close, Volume) candle data from cryptocurrency exchanges, and provides technical analysis capabilities.

## Architecture

```
                    ┌──────────────────────────┐
                    │  candilize-auth (:8081)   │
                    │  gRPC server (:9090)      │
                    │                          │
                    │  - User auth (JWT)       │
                    │  - Config management     │
                    │  - MySQL + Redis         │
                    └────────┬─────────────────┘
                             │
              gRPC (JWT validation)  REST (scheduler config)
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   │
┌─────────────────┐  ┌─────────────────┐         │
│ candilize-market │  │candilize-techni-│         │
│ (:8080, :9091)   │  │cal (:8082)      │         │
│                  │  │                 │         │
│ - Candle data    │  │ - Indicators    │         │
│ - MongoDB        │  │ - Scanner       │         │
│ - Redis cache    │  │ - Strategy      │         │
│ - Kafka pipeline │  │ - Backtest      │         │
│ - Exchange APIs  │  └────────┬────────┘         │
└──────────────────┘           │                  │
         ▲                     │                  │
         │          gRPC (candle data)            │
         └─────────────────────┘                  │
                                                  │
         ┌────────────────────────────────────────┘
         │
         ▼
┌──────────────────┐     ┌──────────┐
│ Binance API      │     │ Kafka    │
│ MEXC API         │     │ (async)  │
└──────────────────┘     └──────────┘
```

## Module Breakdown

| Module | Port | Database | Role |
|---|---|---|---|
| **candilize-auth** | HTTP 8081, gRPC 9090 | MySQL + Redis | Authentication, configuration, JWT tokens |
| **candilize-market** | HTTP 8080, gRPC 9091 | MongoDB + Redis + Kafka | Candle data pipeline — download, store, serve |
| **candilize-technical** | HTTP 8082 | None (client-only) | Technical analysis — indicators, scanner, strategy, backtest |
| **candilize-proto** | — | — | Shared protobuf definitions for gRPC contracts |

## Tech Stack

| Category | Technology | Version |
|---|---|---|
| **Framework** | Spring Boot | 4.0.2 |
| **Language** | Java | 21 |
| **Build** | Maven | Multi-module |
| **Auth** | Spring Security + JWT | — |
| **Relational DB** | MySQL | 8+ |
| **Document DB** | MongoDB | — |
| **Cache** | Redis | — |
| **Message Queue** | Apache Kafka | — |
| **RPC** | gRPC (Spring gRPC 1.0.2) | — |
| **HTTP Client** | WebClient (Spring WebFlux) | — |
| **Migration** | Flyway | — |
| **API Docs** | Swagger/OpenAPI 3 | — |
| **Metrics** | Micrometer + Actuator | — |
| **Code Gen** | Lombok | — |

## Communication Patterns

### REST (External)
- Clients → auth (login, register, config CRUD)
- Clients → market (query candles, trigger downloads)
- Clients → technical (indicators, scanner, strategy, backtest)
- market → auth (fetch scheduler config via internal API)

### gRPC (Internal)
- market → auth (JWT validation)
- technical → auth (JWT validation)
- technical → market (fetch candle data)

### Kafka (Async)
- market internal: PriceDownloadScheduler → KafkaProducerService → get-price topic → KafkaConsumerService → CandleDownloadService

## Design Patterns

| Pattern | Where | How |
|---|---|---|
| **Strategy** | `CandleDataProvider` | Each exchange (Binance, MEXC, Test) has its own implementation |
| **Factory** | `CandleDataProviderFactory` | Selects the right provider by exchange name |
| **Adapter** | `KlineToOhlcvAdapter` | Converts exchange-specific DTOs to domain model |
| **Observer** | Kafka producer/consumer | Decouples download triggers from execution |
| **Builder** | Protobuf messages, Lombok `@Builder` | Immutable object construction |

## Data Flow: End-to-End Candle Query

```
1. Client sends GET /api/v1/candles/BTCUSDT/1h with JWT
2. JwtAuthenticationFilter extracts JWT
3. AuthGrpcClient validates JWT via gRPC → candilize-auth:9090
4. SecurityConfig allows authenticated access
5. CandleController delegates to CandleQueryService
6. @Cacheable checks Redis → cache hit → return
7. ConfigValidationService validates pair/interval via auth REST
8. CandleDataMongoRepository queries MongoDB
9. Response cached in Redis (60s TTL)
10. CandleResponse list returned as JSON
```

## Data Flow: Candle Download

```
1. PriceDownloadScheduler fires on cron (e.g., every 1h)
2. Fetches enabled pairs/intervals from auth service (REST + X-API-Key)
3. For each (pair, interval): sends KafkaPriceRequest to "get-price" topic
4. KafkaConsumerService receives message
5. CandleDownloadService.downloadAndPersist():
   a. CandleDataProviderFactory selects exchange provider
   b. BinanceCandleDataProvider calls Binance API via WebClient
   c. BinanceKlineToOhlcvAdapter converts to Ohlcv domain model
   d. CandleDataPersistenceService saves to MongoDB (skips duplicates)
   e. @CacheEvict clears Redis candle cache
```
