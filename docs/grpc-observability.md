# gRPC Observability & Debugging Guide

## Summary

Candilize uses gRPC for all service-to-service communication. This document covers the observability stack added across all three modules — logging interceptors, Micrometer metrics, health checks, server reflection, debug profiles, and custom actuator endpoints.

**What was added:**
- Server & client logging interceptors — every gRPC call is logged with method, status, and duration
- Server & client metrics interceptors — Micrometer counters and timers per method and status code
- gRPC health checks — `grpc.health.v1.Health/Check` on both servers
- gRPC server reflection — `grpcurl` can discover services without `.proto` files
- Custom `/actuator/grpc` endpoint — REST endpoint showing registered services and client channels
- `grpc-debug` Spring profile — verbose framework-level logging for deep debugging

**No pom.xml changes were needed.** Spring gRPC 1.0.2 already includes `grpc-services` (reflection + health) transitively. Micrometer is already available via `spring-boot-starter-actuator`.

---

## Architecture

```
                         gRPC :9090 (plaintext)
  ┌─────────────────────────────────────────────────────┐
  │  candilize-auth (HTTP :8081)                        │
  │                                                     │
  │  gRPC Server: AuthService                           │
  │    ├─ ValidateToken(token) → valid, username, roles │
  │    └─ GetUserByUsername(username) → found, roles    │
  │                                                     │
  │  Interceptors:                                      │
  │    ├─ GrpcServerMetricsInterceptor                  │
  │    └─ GrpcServerLoggingInterceptor                  │
  │                                                     │
  │  Actuator: /actuator/grpc, /actuator/metrics        │
  └──────────▲──────────────────────▲───────────────────┘
             │                      │
        gRPC │                 gRPC │
             │                      │
  ┌──────────┴──────────┐   ┌──────┴────────────────────┐
  │ candilize-market    │   │ candilize-technical       │
  │ (HTTP :8080)        │   │ (HTTP :8082)              │
  │                     │   │                           │
  │ gRPC Server :9091   │   │ No gRPC server            │
  │   └─ MarketService  │   │ (spring.grpc.server       │
  │       └─ GetCandles │   │  .enabled=false)          │
  │                     │   │                           │
  │ gRPC Clients:       │   │ gRPC Clients:             │
  │   └─ auth :9090     │   │   ├─ auth :9090           │
  │                     │◄──┤   └─ market :9091         │
  │ Interceptors:       │   │                           │
  │  Server:            │   │ Interceptors:             │
  │   ├─ Metrics        │   │   ├─ ClientMetrics        │
  │   └─ Logging        │   │   └─ ClientLogging        │
  │  Client:            │   │                           │
  │   ├─ Metrics        │   │ Actuator:                 │
  │   └─ Logging        │   │   /actuator/grpc          │
  │                     │   │   /actuator/metrics       │
  │ Actuator:           │   └───────────────────────────┘
  │   /actuator/grpc    │
  │   /actuator/metrics │
  └─────────────────────┘
```

### Communication Matrix

| From → To | Channel | Port | RPCs Used |
|---|---|---|---|
| market → auth | `auth` | 9090 | `ValidateToken` (JWT validation on every HTTP request) |
| technical → auth | `auth` | 9090 | `ValidateToken` (JWT validation on every HTTP request) |
| technical → market | `market` | 9091 | `GetCandles` (fetch OHLCV candle data) |

All channels use **plaintext** (no TLS) and **blocking stubs** (synchronous).

---

## Proto Definitions

### auth.proto

```protobuf
service AuthService {
  rpc ValidateToken(ValidateTokenRequest) returns (ValidateTokenResponse);
  rpc GetUserByUsername(GetUserByUsernameRequest) returns (GetUserByUsernameResponse);
}
```

Located at `candilize-proto/src/main/proto/auth.proto`.

### market.proto

```protobuf
service MarketService {
  rpc GetCandles(GetCandlesRequest) returns (GetCandlesResponse);
}
```

Located at `candilize-proto/src/main/proto/market.proto`.

Both use **unary RPCs** (simple request-response, no streaming).

---

## How It Works

### Interceptor Chain

Every gRPC call passes through interceptors in this order:

```
Request → MetricsInterceptor (start timer) → LoggingInterceptor → Service Implementation
Response ← MetricsInterceptor (stop timer, record counter) ← LoggingInterceptor (log result) ← Service
```

**Server interceptors** (`@GlobalServerInterceptor`) are auto-applied to all gRPC services in the module. **Client interceptors** (`@GlobalClientInterceptor`) are auto-applied to all client channels. No manual wiring is needed — Spring gRPC discovers them as beans.

### Logging Interceptor

Wraps the `ServerCall.close()` (server) or `ClientCall.Listener.onClose()` (client) to capture the final status and duration:

```
INFO  gRPC server | auth.AuthService/ValidateToken | OK | 12ms
WARN  gRPC server | market.MarketService/GetCandles | INTERNAL | DB timeout | 1503ms
INFO  gRPC client | auth.AuthService/ValidateToken | OK | 15ms
```

- Successful calls log at `INFO`
- Failed calls log at `WARN` with the status code and error description

### Metrics Interceptor

Uses Micrometer `Timer.Sample` for accurate timing and records two metrics per call:

| Metric | Type | Tags | Description |
|---|---|---|---|
| `grpc.server.calls` | Counter | `method`, `status` | Total server-side call count |
| `grpc.server.call.duration` | Timer | `method`, `status` | Server-side call latency |
| `grpc.client.calls` | Counter | `method`, `status` | Total client-side call count |
| `grpc.client.call.duration` | Timer | `method`, `status` | Client-side call latency |

### JWT Validation Flow

This is the most exercised gRPC path — it runs on every authenticated HTTP request:

```
1. HTTP request with "Authorization: Bearer <jwt>" hits market or technical
2. JwtAuthenticationFilter extracts the token
3. AuthGrpcClient.validateToken(token) sends gRPC call to auth:9090
4. AuthGrpcService validates JWT via JwtTokenProvider
5. Response: valid=true/false, username, roles
6. Filter sets Spring SecurityContext with the returned identity
7. REST controller processes the request
```

### Health & Reflection

Both are auto-enabled by Spring gRPC 1.0.2 when `spring.grpc.server.health.enabled=true` and `spring.grpc.server.reflection.enabled=true`. The `grpc-services` jar (containing `ProtoReflectionService` and `HealthStatusManager`) is a transitive dependency of `spring-grpc-server-spring-boot-starter`.

---

## File Reference

### Interceptors

| File | Module | Type |
|---|---|---|
| `candilize-auth/.../grpc/GrpcServerLoggingInterceptor.java` | auth | Server logging |
| `candilize-auth/.../grpc/GrpcServerMetricsInterceptor.java` | auth | Server metrics |
| `candilize-market/.../grpc/GrpcServerLoggingInterceptor.java` | market | Server logging |
| `candilize-market/.../grpc/GrpcServerMetricsInterceptor.java` | market | Server metrics |
| `candilize-market/.../grpc/GrpcClientLoggingInterceptor.java` | market | Client logging |
| `candilize-market/.../grpc/GrpcClientMetricsInterceptor.java` | market | Client metrics |
| `candilize-technical/.../grpc/GrpcClientLoggingInterceptor.java` | technical | Client logging |
| `candilize-technical/.../grpc/GrpcClientMetricsInterceptor.java` | technical | Client metrics |

### Actuator Endpoints

| File | Module | Endpoint |
|---|---|---|
| `candilize-auth/.../grpc/GrpcInfoEndpoint.java` | auth | `/actuator/grpc` |
| `candilize-market/.../grpc/GrpcInfoEndpoint.java` | market | `/actuator/grpc` |
| `candilize-technical/.../grpc/GrpcInfoEndpoint.java` | technical | `/actuator/grpc` |

### Configuration

| File | What Changed |
|---|---|
| `candilize-auth/src/main/resources/application.properties` | Health, reflection, observation, actuator exposure, logging levels |
| `candilize-market/src/main/resources/application.properties` | Same + client observation |
| `candilize-technical/src/main/resources/application.properties` | Disabled gRPC server, client observation, actuator exposure |
| `candilize-auth/src/main/resources/application-grpc-debug.properties` | Verbose debug profile |
| `candilize-market/src/main/resources/application-grpc-debug.properties` | Verbose debug profile |
| `candilize-technical/src/main/resources/application-grpc-debug.properties` | Verbose debug profile |
| `candilize-auth/.../security/SecurityConfig.java` | `/actuator/health` → `/actuator/**` |
| `candilize-market/.../security/SecurityConfig.java` | `/actuator/health` → `/actuator/**` |
| `candilize-technical/.../security/SecurityConfig.java` | `/actuator/health` → `/actuator/**` |

---

## Usage Guide

### Install grpcurl

```bash
brew install grpcurl
```

### Discover Services (Reflection)

```bash
# List all services on auth server
grpcurl -plaintext localhost:9090 list

# List all services on market server
grpcurl -plaintext localhost:9091 list

# Describe a specific service
grpcurl -plaintext localhost:9090 describe com.mohsindev.candilize.proto.auth.AuthService

# Describe a specific message type
grpcurl -plaintext localhost:9090 describe com.mohsindev.candilize.proto.auth.ValidateTokenRequest
```

### Health Checks

```bash
grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check
grpcurl -plaintext localhost:9091 grpc.health.v1.Health/Check
```

### Call RPCs Directly

```bash
# Validate a JWT token
grpcurl -plaintext \
  -d '{"token": "your-jwt-token"}' \
  localhost:9090 com.mohsindev.candilize.proto.auth.AuthService/ValidateToken

# Look up a user
grpcurl -plaintext \
  -d '{"username": "admin"}' \
  localhost:9090 com.mohsindev.candilize.proto.auth.AuthService/GetUserByUsername

# Fetch candle data
grpcurl -plaintext \
  -d '{"pair":"BTCUSDT","interval_code":"1h","limit":5,"exchange":"binance"}' \
  localhost:9091 com.mohsindev.candilize.proto.market.MarketService/GetCandles
```

### View Metrics

```bash
# Server call count
curl localhost:8081/actuator/metrics/grpc.server.calls

# Server call latency
curl localhost:8081/actuator/metrics/grpc.server.call.duration

# Client call count (market calling auth)
curl localhost:8080/actuator/metrics/grpc.client.calls

# Client call latency
curl localhost:8080/actuator/metrics/grpc.client.call.duration

# Filter by specific method
curl "localhost:8081/actuator/metrics/grpc.server.calls?tag=method:auth.AuthService/ValidateToken"

# Filter by status code
curl "localhost:8081/actuator/metrics/grpc.server.calls?tag=status:OK"
```

### Custom gRPC Info Endpoint

```bash
# Auth — lists gRPC server services and their methods
curl localhost:8081/actuator/grpc | jq

# Market — lists server services + client channels
curl localhost:8080/actuator/grpc | jq

# Technical — lists client channels
curl localhost:8082/actuator/grpc | jq
```

### Enable Verbose Debug Logging

```bash
# Via command line
java -jar candilize-auth.jar --spring.profiles.active=grpc-debug

# Via environment variable
SPRING_PROFILES_ACTIVE=grpc-debug java -jar candilize-market.jar

# Via Maven
mvn spring-boot:run -Dspring-boot.run.profiles=grpc-debug

# In IntelliJ: Run Configuration → Active Profiles → grpc-debug
```

### Using Postman for gRPC

1. Open Postman → **New** → **gRPC Request**
2. Enter server URL: `localhost:9090` (auth) or `localhost:9091` (market)
3. Click **Import .proto** and select files from `candilize-proto/src/main/proto/`
4. Choose a method from the dropdown
5. Fill in the request body (JSON) and click **Invoke**

---

## Important Things for Developers

### Interceptor Registration

Interceptors are registered automatically. Annotating a Spring bean with `@GlobalServerInterceptor` applies it to all gRPC services in that module. `@GlobalClientInterceptor` applies to all client channels. You do **not** need to modify `GrpcClientConfig.java` or any service class.

### Interceptor Ordering

The `Ordered` interface controls execution order. Lower values run first:

| Interceptor | Order | Runs |
|---|---|---|
| MetricsInterceptor | `LOWEST_PRECEDENCE - 20` | First (starts timer before everything) |
| LoggingInterceptor | `LOWEST_PRECEDENCE - 10` | Second (logs after metrics captured) |

### candilize-technical Has No gRPC Server

`candilize-technical` is a client-only module. The gRPC server is explicitly disabled with `spring.grpc.server.enabled=false` in its `application.properties`. This means:
- No `GrpcServiceDiscoverer` bean exists — the `GrpcInfoEndpoint` does not inject it
- `grpcurl` cannot connect to technical (there is no gRPC port)
- Only client-side interceptors and metrics are active

### Actuator Security

Actuator endpoints are open (`/actuator/**` is `permitAll()` in all SecurityConfig files). This is appropriate for development. **For production**, restrict access:

```java
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

Or use a management port:

```properties
management.server.port=9999
```

### Metric Cardinality

The metrics interceptors tag by `method` and `status`. With 3 RPC methods and ~4 status codes, this produces ~12 time series per service — well within safe limits. If you add many more RPCs, the cardinality remains manageable since each unique method+status combination creates one counter and one timer.

### Error Handling Patterns

The two gRPC servers handle errors differently:

- **AuthGrpcService**: Never calls `responseObserver.onError()`. Returns error information inside the response message (`setValid(false)`, `setErrorMessage(...)`). This means the gRPC status is always `OK`, and errors show up in the response body.
- **MarketGrpcService**: Calls `responseObserver.onError(Status.INTERNAL.withDescription(...).asRuntimeException())` on exceptions. This propagates a non-OK gRPC status to the client, which appears as `StatusRuntimeException` on the client side.

Both patterns are valid. The auth approach is friendlier for clients that don't want to catch exceptions. The market approach uses gRPC status codes as intended.

### Blocking Stubs

All clients use `BlockingStub` (synchronous). This means the calling thread blocks until the gRPC response arrives. For the JWT validation path (which runs on every HTTP request), this adds latency equal to the network round-trip + auth processing time. If this becomes a bottleneck, consider:
- Caching validated tokens locally with a short TTL
- Switching to `FutureStub` or `AsyncStub` for non-blocking calls

### No TLS

All gRPC channels use `negotiation-type=plaintext`. Traffic between services is unencrypted. This is fine for `localhost` development but **must be changed for production**. To enable TLS:

```properties
spring.grpc.server.ssl.enabled=true
spring.grpc.server.ssl.certificate=classpath:server.crt
spring.grpc.server.ssl.private-key=classpath:server.key

spring.grpc.client.channels.auth.negotiation-type=tls
spring.grpc.client.channels.auth.ssl.trust-certificate=classpath:ca.crt
```

### Adding a New gRPC Service

1. Define the `.proto` file in `candilize-proto/src/main/proto/`
2. Run `mvn compile` in `candilize-proto` to generate stubs
3. Create a service class extending `*ImplBase`, annotate with `@Service`
4. All existing interceptors (logging, metrics) auto-apply — no extra wiring needed
5. The `/actuator/grpc` endpoint auto-discovers new services
6. Reflection auto-exposes new services to `grpcurl`
