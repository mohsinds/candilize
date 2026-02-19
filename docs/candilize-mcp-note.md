# Candilize – Brief Note for MCP

**Candilize** is a Java 17 **multi-module Maven** project (Spring Boot 4.0.2) of **microservices for crypto market data and technical analysis**.

## Modules

| Module | Role | HTTP Port | gRPC Port | Stack |
|--------|------|-----------|-----------|--------|
| **candilize-proto** | Shared gRPC protobuf definitions (no runtime) | — | — | Protobuf 3.25, gRPC 1.60 |
| **candilize-auth** | Auth, users, JWT, config (pairs/intervals), token validation | 8081 | 9090 | MySQL, Redis, Flyway, Spring Security |
| **candilize-market** | Candles, download pipeline, cache | 8080 | 9091 | MongoDB, Redis, Kafka, Binance/MEXC |
| **candilize-technical** | Indicators, scanner, strategies, backtesting | 8082 | — | Web + gRPC client to market |

## Main Capabilities

- **Auth (candilize-auth):** Register, login, refresh JWT; REST config for pairs/intervals/exchanges; internal REST for scheduler config; gRPC for token validation.
- **Market (candilize-market):** REST candles (`/api/v1/candles/{pair}/{interval}`), download/backfill, cache refresh; scheduled downloads via Kafka; gRPC `GetCandles`; exchanges: Binance (default), MEXC.
- **Technical (candilize-technical):** REST for indicators (e.g. SMA), scanner, strategies, backtest; uses candilize-proto and gRPC to get candle data.

## Integration

- **Auth ↔ Market:** Market calls Auth's internal API for scheduler config (API key). Market validates tokens via Auth's gRPC (localhost:9090).
- **Technical ↔ Market:** Technical uses gRPC client to Market for candle data.
- **Shared:** `candilize-proto` for gRPC contracts; Redis for caching; JWT for REST auth (Bearer).

## Local Defaults

- MySQL: `localhost:3306/candilize1` (Auth).
- MongoDB: `localhost:27071/candilize` (Market).
- Redis: `localhost:6379`.
- Kafka: `localhost:9092`, topic `get-price`.
- Env: `DB_PASSWORD`, `JWT_SECRET`, `INTERNAL_API_KEY`, `MONGODB_URI`, `AUTH_SERVICE_URL` (optional; have dev defaults).

## Build / Run

- Root: `mvn clean install`. Run each service from its module (e.g. `candilize-auth`, `candilize-market`, `candilize-technical`) as Spring Boot apps. JaCoCo and Sonar are configured at root.
