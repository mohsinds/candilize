# Configuration — JacksonConfig & Application Properties

## JacksonConfig — JSON Serialization

**File**: `candilize-technical/.../configuration/JacksonConfig.java`

### What This File Does

Configures Jackson — the JSON library that Spring uses to convert Java objects to/from JSON. Every `@RequestBody` and `ResponseEntity` uses Jackson.

```java
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())              // 1
            .disable(SerializationFeature
                .WRITE_DATES_AS_TIMESTAMPS);                   // 2
    }
}
```

| # | What it does |
|---|---|
| 1 | `JavaTimeModule` — adds support for Java 8+ date/time types (`Instant`, `LocalDateTime`, `ZonedDateTime`). Without this module, Jackson would fail when trying to serialize `IndicatorResult.timestamp` or `StrategySignal.timestamp`. |
| 2 | `WRITE_DATES_AS_TIMESTAMPS` disabled — dates are serialized as ISO-8601 strings (`"2025-01-15T10:30:00Z"`) instead of numeric timestamps (`1736937000`). |

### Where ObjectMapper is Used

| Class | Usage |
|---|---|
| REST controllers | Automatic: Spring uses it to serialize `ResponseEntity` bodies |
| `AuthEntryPoint` | Manual: `objectMapper.writeValue(response.getOutputStream(), error)` |
| `BacktestController` | Automatic: deserializes `@RequestBody BacktestRequest` from JSON |

---

## Application Properties

**File**: `candilize-technical/src/main/resources/application.properties`

```properties
# ===== Application =====
spring.application.name=candilize-technical                    # 1
server.port=8082                                                # 2

# ===== gRPC Clients =====
spring.grpc.server.enabled=false                               # 3
spring.grpc.client.observation.enabled=true                    # 4
spring.grpc.client.channels.auth.address=static://localhost:9090    # 5
spring.grpc.client.channels.auth.negotiation-type=plaintext
spring.grpc.client.channels.market.address=static://localhost:9091  # 6
spring.grpc.client.channels.market.negotiation-type=plaintext

# ===== Actuator =====
management.endpoints.web.exposure.include=health,info,metrics,grpc  # 7
management.endpoint.health.show-details=always
management.endpoint.metrics.enabled=true

# ===== gRPC Logging =====
logging.level.io.grpc=WARN                                     # 8
logging.level.org.springframework.grpc=INFO

# ===== Auth REST URL =====
app.auth-service.url=${AUTH_SERVICE_URL:http://localhost:8081}  # 9
```

| # | What it does |
|---|---|
| 1 | Application name — shown in logs, metrics, and service discovery. |
| 2 | HTTP port 8082 — avoids conflict with auth (8081) and market (8080). |
| 3 | `spring.grpc.server.enabled=false` — this module is a gRPC **client only**. It doesn't expose any gRPC services. Without this, Spring gRPC would start a server on a default port. |
| 4 | `observation.enabled=true` — enables Micrometer observation for gRPC client calls (metrics, tracing). |
| 5 | Auth channel — connects to `candilize-auth` gRPC server at `localhost:9090`. Used by `AuthGrpcClient` for JWT validation. |
| 6 | Market channel — connects to `candilize-market` gRPC server at `localhost:9091`. Used by `MarketGrpcClient` for candle data. |
| 7 | Actuator endpoints exposed: health check, app info, Prometheus metrics, and custom gRPC info endpoint. |
| 8 | gRPC logging levels — WARN for gRPC internals (very noisy at DEBUG), INFO for Spring gRPC framework. Use `grpc-debug` profile for verbose logging. |
| 9 | Auth service REST URL — used if any REST calls to auth are needed. Defaults to `localhost:8081`, overridable via `AUTH_SERVICE_URL` environment variable. |

### Port Assignments

| Module | HTTP Port | gRPC Port |
|---|---|---|
| candilize-auth | 8081 | 9090 |
| candilize-market | 8080 | 9091 |
| candilize-technical | 8082 | — (client only) |

### Debug Profile

**File**: `candilize-technical/src/main/resources/application-grpc-debug.properties`

Activate with `--spring.profiles.active=grpc-debug` for verbose gRPC logging:

```properties
logging.level.io.grpc=DEBUG
logging.level.org.springframework.grpc=DEBUG
logging.level.io.grpc.netty=DEBUG
logging.level.com.mohsindev.candilize.technical.grpc=DEBUG
```
