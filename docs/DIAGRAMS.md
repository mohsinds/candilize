# Candilize — Architecture Diagrams

Mermaid diagrams for GitHub. These render automatically when viewing this file on GitHub.

---

## System Architecture (flowchart)

```mermaid
flowchart TB
    subgraph Clients["Clients"]
        Web[Web App]
        Mobile[Mobile]
        API[External APIs]
    end

    subgraph Services["Microservices"]
        Auth[candilize-auth<br/>:8081 / gRPC :9090]
        Market[candilize-market<br/>:8080 / gRPC :9091]
        Technical[candilize-technical<br/>:8082]
    end

    subgraph Data["Data Stores"]
        MySQL[(MySQL<br/>users, pairs, intervals)]
        MongoDB[(MongoDB<br/>candle_data)]
        Redis[(Redis<br/>cache)]
    end

    subgraph External["External"]
        Kafka[Kafka<br/>get-price]
        Binance[Binance API]
        MEXC[MEXC API]
    end

    Web -->|REST JWT| Auth
    Web -->|REST JWT| Market
    Web -->|REST JWT| Technical
    Mobile -->|REST JWT| Auth
    Mobile -->|REST JWT| Market
    API -->|REST JWT| Market

    Auth --> MySQL
    Auth --> Redis
    Market --> MongoDB
    Market --> Redis
    Market --> Kafka
    Kafka --> Market
    Market --> Binance
    Market --> MEXC

    Market -->|gRPC ValidateToken| Auth
    Technical -->|gRPC ValidateToken| Auth
    Technical -->|gRPC GetCandles| Market
    Market -->|REST X-API-Key| Auth
```

---

## Kafka Flow (sequenceDiagram)

```mermaid
sequenceDiagram
    participant Scheduler as PriceDownloadScheduler
    participant Client as SchedulerConfigClient
    participant Auth as candilize-auth
    participant Producer as KafkaProducerService
    participant Kafka as Kafka get-price
    participant Consumer as KafkaConsumerService
    participant Download as CandleDownloadService
    participant Exchange as Binance/MEXC
    participant Mongo as MongoDB

    Note over Scheduler,Kafka: Cron triggers (1m, 5m, 15m, etc.)
    Scheduler->>Client: fetchSchedulerConfig()
    Client->>Auth: GET /api/v1/internal/scheduler-config<br/>X-API-Key
    Auth-->>Client: SchedulerConfigResponse (pairs, intervals)
    Client-->>Scheduler: Optional<config>

    loop For each (pair, interval)
        Scheduler->>Producer: sendProducerRequest(KafkaPriceRequest)
        Producer->>Kafka: Produce(pair, request)
    end

    Kafka->>Consumer: Consume(message)
    Consumer->>Download: downloadAndPersist(priceObject)
    Download->>Exchange: GET /api/v3/klines (candles)
    Exchange-->>Download: OHLCV data
    Download->>Mongo: persistCandles()
    Mongo-->>Download: count saved
```

---

## Microservices Overview (graph TD)

```mermaid
graph TD
    subgraph Proto["candilize-proto"]
        AuthProto[auth.proto]
        MarketProto[market.proto]
    end

    subgraph Auth["candilize-auth :8081"]
        AuthREST[REST: register, login, config]
        AuthGRPC[gRPC: ValidateToken, GetUserByUsername]
        AuthInternal[Internal: scheduler-config]
    end

    subgraph Market["candilize-market :8080"]
        MarketREST[REST: candles, download, cache]
        MarketGRPC[gRPC: GetCandles]
        MarketKafka[Kafka producer/consumer]
    end

    subgraph Technical["candilize-technical :8082"]
        TechREST[REST: indicator, scanner, backtest]
        TechAuthClient[gRPC client → Auth]
        TechMarketClient[gRPC client → Market]
    end

    subgraph Infra["Infrastructure"]
        MySQL[(MySQL)]
        Mongo[(MongoDB)]
        Redis[(Redis)]
        Kafka[Kafka]
    end

    AuthProto --> Auth
    AuthProto --> Market
    MarketProto --> Market
    MarketProto --> Technical

    Auth --> MySQL
    Auth --> Redis
    Market --> Mongo
    Market --> Redis
    Market --> Kafka
    Kafka --> Market

    TechAuthClient -->|ValidateToken| AuthGRPC
    TechMarketClient -->|GetCandles| MarketGRPC
    Market -->|ValidateToken| AuthGRPC
    Market -->|X-API-Key| AuthInternal
```

---

## User Journey (flowchart LR)

```mermaid
flowchart LR
    subgraph AuthFlow["Authentication"]
        A1[Register] --> A2[Login]
        A2 --> A3[JWT Tokens]
    end

    subgraph ReadFlow["Read Candles"]
        B1[GET /candles/BTCUSDT/1h] --> B2{JWT valid?}
        B2 -->|Yes| B3[Market: gRPC to Auth]
        B3 --> B4[Check Redis cache]
        B4 -->|Hit| B5[Return cached]
        B4 -->|Miss| B6[Query MongoDB]
        B6 --> B5
    end

    subgraph AdminFlow["Admin: Config"]
        C1[GET /config/pairs] --> C2{JWT + ADMIN?}
        C2 -->|Yes| C3[Auth: MySQL]
        C3 --> C4[Return pairs]
    end

    subgraph DownloadFlow["Trigger Download"]
        D1[POST /download] --> D2{JWT + ADMIN?}
        D2 -->|Yes| D3[Send to Kafka]
        D3 --> D4[Consumer fetches exchange]
        D4 --> D5[Persist MongoDB]
    end

    A3 --> B1
    A3 --> C1
    A3 --> D1
```
