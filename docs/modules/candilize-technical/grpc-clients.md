# gRPC Clients — AuthGrpcClient, MarketGrpcClient, GrpcClientConfig

## AuthGrpcClient — JWT Validation

**File**: `candilize-technical/.../grpc/AuthGrpcClient.java`

### What This File Does

A gRPC client that calls the `candilize-auth` service (port 9090) to validate JWT tokens. Every HTTP request to the technical module extracts the JWT from the `Authorization` header and sends it here for verification.

### Full Source with Commentary

```java
@Slf4j
@Component                                                    // 1
@RequiredArgsConstructor
public class AuthGrpcClient {

    private final AuthServiceGrpc.AuthServiceBlockingStub
        authStub;                                              // 2

    public ValidateResult validateToken(String token) {
        if (token == null || token.isBlank()) {                // 3
            return ValidateResult.invalid("Token is empty");
        }
        try {
            ValidateTokenResponse response =
                authStub.validateToken(                        // 4
                    ValidateTokenRequest.newBuilder()
                        .setToken(token)
                        .build());
            if (response.getValid()) {                         // 5
                return ValidateResult.valid(
                    response.getUsername(),
                    response.getRolesList());
            }
            return ValidateResult.invalid(
                response.getErrorMessage());                   // 6
        } catch (StatusRuntimeException e) {                   // 7
            log.debug("Auth gRPC call failed: {}",
                e.getStatus());
            return ValidateResult.invalid(
                e.getStatus().getDescription());
        } catch (Exception e) {
            log.debug("Token validation failed: {}",
                e.getMessage());
            return ValidateResult.invalid(e.getMessage());
        }
    }

    public record ValidateResult(                              // 8
            boolean valid,
            String username,
            List<String> roles,
            String errorMessage) {
        public static ValidateResult valid(
                String username, List<String> roles) {
            return new ValidateResult(
                true, username, roles, null);
        }
        public static ValidateResult invalid(String error) {
            return new ValidateResult(
                false, null, List.of(), error);
        }
    }
}
```

| # | What it does |
|---|---|
| 1 | `@Component` — Spring-managed bean. Injected into `JwtAuthenticationFilter`. |
| 2 | `AuthServiceGrpc.AuthServiceBlockingStub` — the gRPC client stub. Generated from `auth.proto`. "Blocking" means the call waits for the response. Created by `GrpcClientConfig`. |
| 3 | Early return for empty tokens — avoids a network round-trip. |
| 4 | `authStub.validateToken(...)` — sends the token over gRPC to `candilize-auth:9090`. This is a blocking network call. |
| 5 | If `valid=true`, extract username and roles from the response. |
| 6 | If `valid=false`, return the error message from the auth service. |
| 7 | `StatusRuntimeException` — thrown when the gRPC call itself fails (e.g., auth service is down, network timeout). |
| 8 | `ValidateResult` — a Java record that encapsulates the validation result. Uses factory methods `valid()` and `invalid()` for clean construction. |

### The Record Pattern

`ValidateResult` is a **Java record** (Java 16+). Records are immutable data classes — the compiler generates:
- A constructor with all fields
- Getter methods (e.g., `valid()`, `username()`)
- `equals()`, `hashCode()`, `toString()`

The static factory methods (`valid()`, `invalid()`) make it clear at the call site whether you're creating a success or failure result.

---

## MarketGrpcClient — Candle Data Fetching

**File**: `candilize-technical/.../grpc/MarketGrpcClient.java`

### What This File Does

A gRPC client that calls the `candilize-market` service (port 9091) to fetch OHLCV candle data. Used by IndicatorService, ScannerService, StrategyService, and BacktestService to get market data for analysis.

### Full Source with Commentary

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketGrpcClient {

    private final MarketServiceGrpc.MarketServiceBlockingStub
        marketStub;                                            // 1

    public List<Candle> getCandles(
            String pair,
            String intervalCode,
            int limit,
            Long startTime,
            Long endTime,
            String exchange) {
        try {
            GetCandlesRequest.Builder builder =
                GetCandlesRequest.newBuilder()                  // 2
                    .setPair(pair)
                    .setIntervalCode(intervalCode)
                    .setLimit(limit > 0 ? limit : 100);        // 3
            if (startTime != null && startTime > 0)
                builder.setStartTime(startTime);               // 4
            if (endTime != null && endTime > 0)
                builder.setEndTime(endTime);
            if (exchange != null && !exchange.isBlank())
                builder.setExchange(exchange);

            GetCandlesResponse response =
                marketStub.getCandles(builder.build());        // 5
            return response.getCandlesList();                   // 6
        } catch (StatusRuntimeException e) {                   // 7
            log.warn("Market gRPC call failed: {}",
                e.getStatus());
            throw new RuntimeException(
                "Failed to fetch candles: "
                    + e.getStatus().getDescription());
        }
    }
}
```

| # | What it does |
|---|---|
| 1 | `MarketServiceBlockingStub` — gRPC stub for the market service. Created by `GrpcClientConfig`. |
| 2 | `GetCandlesRequest.newBuilder()` — protobuf builder pattern. All protobuf objects are immutable; you build them with a builder. |
| 3 | Default limit of 100 if not specified or invalid. |
| 4 | Optional fields — only set if provided. Protobuf fields default to 0/empty, so we check before setting. |
| 5 | `marketStub.getCandles(...)` — blocking gRPC call to `candilize-market:9091`. |
| 6 | `getCandlesList()` — returns the `repeated Candle` field as a Java `List<Candle>`. |
| 7 | Unlike `AuthGrpcClient` which returns a result object, this throws an exception on failure — the caller handles errors. |

### Difference: AuthGrpcClient vs MarketGrpcClient Error Handling

| | AuthGrpcClient | MarketGrpcClient |
|---|---|---|
| On failure | Returns `ValidateResult.invalid(...)` | Throws `RuntimeException` |
| Why | JWT validation failures are expected (expired tokens) | Data fetch failures are exceptional |
| Caller pattern | `if (result.valid()) { ... }` | Try-catch in controller |

---

## GrpcClientConfig — Channel & Stub Creation

**File**: `candilize-technical/.../grpc/GrpcClientConfig.java`

### What This File Does

Creates gRPC blocking stubs for both the auth and market services. Channel addresses are configured in `application.properties`.

```java
@Configuration
public class GrpcClientConfig {

    @Bean                                                      // 1
    AuthServiceGrpc.AuthServiceBlockingStub
            authServiceBlockingStub(
                GrpcChannelFactory channelFactory) {           // 2
        return AuthServiceGrpc.newBlockingStub(
            channelFactory.createChannel("auth"));             // 3
    }

    @Bean
    MarketServiceGrpc.MarketServiceBlockingStub
            marketServiceBlockingStub(
                GrpcChannelFactory channelFactory) {
        return MarketServiceGrpc.newBlockingStub(
            channelFactory.createChannel("market"));           // 4
    }
}
```

| # | What it does |
|---|---|
| 1 | `@Bean` — registers the stub in Spring's IoC container. Injected into `AuthGrpcClient` and `MarketGrpcClient`. |
| 2 | `GrpcChannelFactory` — Spring gRPC's factory for creating managed channels. Reads configuration from properties. |
| 3 | `channelFactory.createChannel("auth")` — creates a channel named `"auth"`. The address comes from `spring.grpc.client.channels.auth.address=static://localhost:9090`. |
| 4 | `channelFactory.createChannel("market")` — creates the market channel from `spring.grpc.client.channels.market.address=static://localhost:9091`. |

### Channel Configuration (application.properties)

```properties
spring.grpc.client.channels.auth.address=static://localhost:9090
spring.grpc.client.channels.auth.negotiation-type=plaintext
spring.grpc.client.channels.market.address=static://localhost:9091
spring.grpc.client.channels.market.negotiation-type=plaintext
```

`static://` means a fixed address. `plaintext` means no TLS (appropriate for local/development). In production, you'd use `dns:///` and TLS.
