# candilize-technical Module

## What This Module Does

The **technical module** is the technical analysis engine. It:

1. **Computes technical indicators** (SMA, EMA, RSI, etc.) using candle data from the market service
2. **Scans the market** for symbols meeting specific criteria (e.g., SMA crossover)
3. **Generates trading signals** (BUY/SELL/HOLD) based on configurable strategies
4. **Backtests strategies** against historical candle data
5. **Validates JWT tokens** by calling the auth service over gRPC

## Tech Stack

| Technology | Purpose |
|---|---|
| Spring Boot 4.0.2 | Application framework |
| Spring Security | JWT validation via gRPC |
| Spring gRPC (client) | Fetches candle data from market module, validates JWT via auth module |

## Architecture Overview

```
HTTP :8082
+-----------------------------+
| REST Controllers            |
|  +-- IndicatorController    |
|  +-- ScannerController      |
|  +-- StrategyController     |
|  +-- BacktestController     |
+-----------------------------+
| Security (JWT via gRPC)     |
|  +-- JwtAuthFilter --------+---gRPC---> auth:9090
|  +-- AuthEntryPoint         |
+-----------------------------+
| Services                    |
|  +-- IndicatorService       |
|  +-- ScannerService         |
|  +-- StrategyService        |
|  +-- BacktestService        |
+-----------------------------+
| gRPC Clients                |
|  +-- AuthGrpcClient --------+---gRPC---> auth:9090
|  +-- MarketGrpcClient ------+---gRPC---> market:9091
+-----------------------------+
```

## REST API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/indicator/{pair}/{interval}/sma` | JWT | Compute SMA indicator |
| GET | `/api/v1/scanner` | JWT | Scan market for criteria |
| GET | `/api/v1/strategy/{strategy}/{pair}/{interval}` | JWT | Get strategy signals |
| POST | `/api/v1/backtest` | JWT | Run strategy backtest |

## Current Status

This module has the full REST API and gRPC client infrastructure in place. The service implementations are currently stubs (TODO) that return empty results. The next step is implementing the actual indicator calculations, scanner logic, strategy engines, and backtest simulation.

## File Reference

### Controllers
- [controllers.md](controllers.md) — IndicatorController, ScannerController, StrategyController, BacktestController

### gRPC
- [grpc-clients.md](grpc-clients.md) — AuthGrpcClient, MarketGrpcClient, GrpcClientConfig

### Security
- [security.md](security.md) — SecurityConfig, JwtAuthenticationFilter, AuthEntryPoint

### Services & Models
- [services.md](services.md) — All service classes and data model records

### Configuration
- [configuration.md](configuration.md) — JacksonConfig, application.properties
