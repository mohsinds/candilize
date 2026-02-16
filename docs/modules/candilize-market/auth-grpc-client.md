# AuthGrpcClient — JWT Validation via gRPC

**File**: `candilize-market/.../grpc/AuthGrpcClient.java`

## What This File Does

A gRPC client that calls `candilize-auth` (port 9090) to validate JWT tokens. Every HTTP request to the market module extracts the JWT and sends it here for verification.

## Full Source with Commentary

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGrpcClient {

    private final AuthServiceGrpc.AuthServiceBlockingStub
        authStub;                                              // 1

    public ValidateResult validateToken(String token) {
        if (token == null || token.isBlank()) {                // 2
            return ValidateResult.invalid("Token is empty");
        }
        try {
            ValidateTokenResponse response =
                authStub.validateToken(                        // 3
                    ValidateTokenRequest.newBuilder()
                        .setToken(token)
                        .build());
            if (response.getValid()) {                         // 4
                return ValidateResult.valid(
                    response.getUsername(),
                    response.getRolesList());
            }
            return ValidateResult.invalid(
                response.getErrorMessage());
        } catch (StatusRuntimeException e) {                   // 5
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

    public record ValidateResult(                              // 6
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
| 1 | `AuthServiceBlockingStub` — gRPC blocking stub created by `GrpcClientConfig`. "Blocking" means the call waits for the response. |
| 2 | Early return for empty tokens — avoids a network round-trip. |
| 3 | `authStub.validateToken(...)` — sends token over gRPC to `candilize-auth:9090`. |
| 4 | Auth service returns `valid=true` with username and roles, or `valid=false` with error message. |
| 5 | `StatusRuntimeException` — thrown when the gRPC call itself fails (auth service down, network timeout). |
| 6 | `ValidateResult` — Java record with static factory methods `valid()` and `invalid()` for clean construction. |

## The Record Pattern

`ValidateResult` is a **Java record** (Java 16+) — an immutable data class. The compiler generates:
- Constructor with all fields
- Accessor methods: `valid()`, `username()`, `roles()`, `errorMessage()`
- `equals()`, `hashCode()`, `toString()`

The static factory methods make it clear at the call site:
```java
ValidateResult.valid("admin", List.of("ROLE_ADMIN"))
ValidateResult.invalid("Token expired")
```

## How It's Used

```
JwtAuthenticationFilter.doFilterInternal()
  → authGrpcClient.validateToken(jwt)
    → gRPC to auth:9090
    ← ValidateResult(valid=true, username="admin", roles=["ROLE_ADMIN"])
  → SecurityContextHolder.setAuthentication(admin, [ROLE_ADMIN])
```

This is identical to how `candilize-technical` validates tokens — both modules use the same pattern.
