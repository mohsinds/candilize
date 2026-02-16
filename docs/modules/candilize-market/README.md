# candilize-market Module

## What This Module Does

The **market module** is the data pipeline for cryptocurrency candle data. It:

1. **Downloads candle (OHLCV) data** from exchanges (Binance, MEXC) via their REST APIs
2. **Stores data in MongoDB** for historical queries
3. **Serves candle data** via REST API and gRPC
4. **Schedules periodic downloads** using cron jobs
5. **Uses Kafka** as a message queue for download requests
6. **Validates JWT tokens** by calling the auth service over gRPC

## Tech Stack

| Technology | Purpose |
|---|---|
| Spring Boot 4.0.2 | Application framework |
| Spring Security | JWT validation via gRPC |
| MongoDB | Document store for candle data |
| Redis | Cache for candle queries and config |
| Apache Kafka | Message queue for download pipeline |
| Spring gRPC (server) | Serves candle data to technical module |
| Spring gRPC (client) | Validates JWT via auth module |
| WebClient | HTTP client for exchange APIs |
| Spring Retry | Retry failed exchange API calls |
| Spring Scheduling | Cron-based candle downloads |

## Architecture Overview

```
HTTP :8080                            gRPC :9091
┌───────────────────────────┐        ┌───────────────────┐
│ REST Controllers          │        │ MarketGrpcService  │
│  ├── CandleController     │        │  └── GetCandles    │
│  ├── DownloadController   │        └───────────────────┘
│  └── CacheController      │               │
├───────────────────────────┤               │
│ Security (JWT via gRPC)   │               │
│  ├── JwtAuthFilter ──────►│──gRPC──► auth:9090
│  └── AuthGrpcClient       │
├───────────────────────────┤
│ Services                  │
│  ├── CandleQueryService   │──── MongoDB (candle_data)
│  ├── CandleDownloadService│
│  ├── PersistenceService   │
│  ├── ConfigValidation     │──── REST → auth:8081
│  ├── KafkaProducerService │──┐
│  └── KafkaConsumerService │◄─┤── Kafka (get-price)
├───────────────────────────┤  │
│ PriceDownloadScheduler    │──┘
├───────────────────────────┤
│ Exchange Providers        │
│  ├── BinanceProvider ─────│──── Binance API
│  ├── MexcProvider ────────│──── MEXC API
│  └── TestProvider         │     (dummy data)
├───────────────────────────┤
│ Config                    │
│  ├── CacheConfig → Redis  │──── Redis (:6379)
│  ├── KafkaConfig          │
│  ├── ExchangeProperties   │
│  └── GrpcClientConfig     │
└───────────────────────────┘
```

## Data Flow: How Candle Data Gets Downloaded

```
1. PriceDownloadScheduler fires on cron schedule (e.g., every 1h)
2. Fetches enabled pairs/intervals from auth service (REST)
3. For each (pair, interval), sends KafkaPriceRequest to "get-price" topic
4. KafkaConsumerService receives the message
5. CandleDownloadService.downloadAndPersist():
   a. CandleDataProviderFactory selects the right exchange provider
   b. BinanceCandleDataProvider calls Binance API via BinanceDataClient
   c. BinanceKlineToOhlcvAdapter converts raw response to Ohlcv domain model
   d. CandleDataPersistenceService saves to MongoDB (skips duplicates)
```

## REST API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/candles/{pair}/{interval}` | JWT | Get candle data |
| GET | `/api/v1/candles/{pair}` | JWT | List available intervals for a pair |
| POST | `/api/v1/download` | JWT + ADMIN | Trigger manual candle download |
| POST | `/api/v1/download/backfill` | JWT + ADMIN | Trigger historical backfill |
| GET | `/api/v1/cache/refresh/{pair}/{interval}/{limit}` | JWT | Refresh cached data |

## gRPC Endpoints

| Service | Method | Purpose |
|---|---|---|
| `MarketService` | `GetCandles` | Fetch candle data (called by technical module) |

## Design Patterns Used

| Pattern | Where | Purpose |
|---|---|---|
| **Strategy** | `CandleDataProvider` interface | Each exchange has its own implementation (Binance, MEXC, Test) |
| **Factory** | `CandleDataProviderFactory` | Selects the right provider based on exchange name |
| **Adapter** | `KlineToOhlcvAdapter` | Converts exchange-specific DTOs to a common domain model |
| **Observer** | Kafka producer/consumer | Decouples download triggers from download execution |

## File Reference

### Controllers
- [candle-controller.md](candle-controller.md) — CandleController (query candle data)
- [download-controller.md](download-controller.md) — DownloadController (trigger downloads)
- [cache-controller.md](cache-controller.md) — CacheController (refresh cache)

### gRPC
- [market-grpc-service.md](market-grpc-service.md) — MarketGrpcService (gRPC server)
- [auth-grpc-client.md](auth-grpc-client.md) — AuthGrpcClient (JWT validation)

### Security
- [security.md](security.md) — SecurityConfig, JwtAuthenticationFilter (gRPC-based)

### Services
- [services.md](services.md) — All service classes, Kafka, scheduler

### Data Layer
- [data-layer.md](data-layer.md) — MongoDB document, repositories, exchange providers

### Configuration
- [configuration.md](configuration.md) — CacheConfig, KafkaConfig, ExchangeProperties, GrpcClientConfig
