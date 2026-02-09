# Candilize

Candilize is a service for fetching and serving OHLCV (candle) market data from multiple crypto exchanges. It exposes REST APIs for candle data and is designed to evolve into a distributed system with Kafka, Redis, and MongoDB.

## Tech Stack

- **Java 17**
- **Spring Boot 4**
- **Spring Web** (REST), **Spring WebFlux** (exchange API clients)
- **Spring Kafka** (for planned async pipeline)
- **Lombok**

## Current Features

- **Multi-exchange support**: MEXC and Binance (Strategy pattern; more exchanges can be added).
- **REST API** for OHLCV candles by trading pair, interval, and limit.
- **Configurable default exchange** and per-request exchange override.
- **Cache refresh endpoint** to trigger fetching and persisting market data (MongoDB integration planned).

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/candles/{pair}/{interval}/{limit}` | Returns OHLCV candles. Optional query param: `exchange` (e.g. `mexc`, `binance`). |
| GET | `/api/v1/cache/refresh/{pair}/{interval}/{limit}` | Triggers fetch and (planned) store of market data. |

**Example:**  
`GET http://localhost:8080/api/v1/candles/BTCUSDT/1d/100`  
(Use no trailing slash. Intervals: `1m`, `3m`, `5m`, `15m`, `30m`, `1h`, `2h`, `4h`, `1d`, `1w` — case-insensitive.)

### Configuration

In `application.properties`:

- `exchange.mexc.base-url`, `exchange.binance.base-url` — exchange API base URLs.
- `exchange.default-exchange` — default exchange when `exchange` query param is omitted (e.g. `mexc`).

## Project Plan: Target Architecture

The system is planned as a distributed, event-driven pipeline for price/candle data:

```
┌─────────────────────────────────────┐
│  Request Authentication & Auth      │
└─────────────────┬───────────────────┘
                  ▼
┌─────────────────────────────────────┐
│  Controller                          │
└─────────────────┬───────────────────┘
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│  Kafka Broker                                                     │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │ Producer Java Svc   │───►│ Messaging Queue (topic=Get price)│ │
│  └─────────────────────┘    │  Partition 1 | Partition 2 | …  │ │
│                              └──────────────┬──────────────────┘ │
│                                             │ consume             │
│  ┌─────────────────────┐                    ▼                     │
│  │ Consumer (Subscribe │    ┌─────────────────────────────────┐ │
│  │ to "Get Price")     │◄───│ Get Price topic messages        │ │
│  └─────────┬───────────┘    └─────────────────────────────────┘ │
└────────────│─────────────────────────────────────────────────────┘
             ▼
┌─────────────────────────────────────┐
│  Pricing Service                     │◄──────► Redis Cache        │
│  (cache-aside: Redis → Mongo)        │◄──────► Mongo Database    │
└─────────────────────────────────────┘
```

### Flow

1. **Auth** — Incoming requests are authenticated and authorized.
2. **Controller** — Handles the request and forwards work to a **Producer Java Service**.
3. **Producer** — Publishes a “Get price” request to the Kafka topic **Get price**.
4. **Consumer** — Subscribes to **Get price**, consumes messages, and passes them to the **Pricing Service**.
5. **Pricing Service** — Resolves price/candle data using a **cache-aside** pattern:
   - Read from **Redis** first.
   - On cache miss, read from **MongoDB** (and optionally populate Redis).
   - Writes/updates go to **MongoDB** (and optionally to Redis).

This keeps the HTTP layer thin and offloads data resolution and persistence to an async, scalable pipeline with Redis for speed and MongoDB for durability.

## Run Locally

```bash
./mvnw spring-boot:run
```

Server runs at `http://localhost:8080`. Actuator endpoints are available under `/actuator`.

## Project Structure (high level)

- `api/` — REST controllers (candles, cache).
- `service/` — Application service (e.g. `CandleService`) and strategy selection.
- `domain/` — Domain model (e.g. `Ohlcv`).
- `infrastructure/market/` — Exchange-specific clients and adapters (MEXC, Binance); `CandleDataProvider` (strategy), `CandleDataService`, `KlineToOhlcvAdapter`.
- `configuration/` — WebClient and exchange properties.
- `enums/` — `CandleInterval`; infrastructure enums (e.g. `ExchangeName`).

## License

See repository or project metadata.
