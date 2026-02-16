# AuthGrpcService — gRPC Token Validation Server

**File**: `candilize-auth/src/main/java/com/mohsindev/candilize/auth/grpc/AuthGrpcService.java`

## What This File Does

This class is a **gRPC server** that listens on port 9090. Other microservices (market, technical) call it to validate JWT tokens. Every time an HTTP request hits market or technical, they extract the JWT and send it here over gRPC to verify it.

---

## gRPC vs REST — Why Use Both?

| | REST (HTTP/JSON) | gRPC (HTTP/2 + Protobuf) |
|---|---|---|
| **Used for** | External clients (browser, mobile) | Internal service-to-service |
| **Data format** | JSON (text, human-readable) | Protobuf (binary, compact) |
| **Speed** | Slower (text parsing, larger payload) | Faster (~10x smaller payload) |
| **Contract** | Loose (any JSON shape) | Strict (defined in .proto file) |
| **In Candilize** | All REST controllers | Token validation, candle fetching |

---

## Full Source with Commentary

```java
@Slf4j
@Service                                                      // 1
@RequiredArgsConstructor
public class AuthGrpcService
        extends AuthServiceGrpc.AuthServiceImplBase {          // 2

    private final JwtTokenProvider jwtTokenProvider;           // 3
    private final UserDetailsService userDetailsService;

    @Override
    public void validateToken(                                 // 4
            ValidateTokenRequest request,                      // 5
            StreamObserver<ValidateTokenResponse> responseObserver) {  // 6
        String token = request.getToken();                     // 7
        try {
            if (token == null || token.isBlank()) {            // 8
                responseObserver.onNext(
                    ValidateTokenResponse.newBuilder()
                        .setValid(false)
                        .setErrorMessage("Token is empty")
                        .build());
                responseObserver.onCompleted();
                return;
            }
            if (!jwtTokenProvider.validateToken(token)) {      // 9
                responseObserver.onNext(
                    ValidateTokenResponse.newBuilder()
                        .setValid(false)
                        .setErrorMessage("Invalid or expired token")
                        .build());
                responseObserver.onCompleted();
                return;
            }
            String username =
                jwtTokenProvider.getUsernameFromToken(token);   // 10
            UserDetails user =
                userDetailsService.loadUserByUsername(username); // 11
            List<String> roles = user.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList());                  // 12
            responseObserver.onNext(
                ValidateTokenResponse.newBuilder()
                    .setValid(true)
                    .setUsername(username)
                    .addAllRoles(roles)
                    .build());                                  // 13
        } catch (Exception e) {
            log.debug("Token validation failed: {}",
                e.getMessage());
            responseObserver.onNext(
                ValidateTokenResponse.newBuilder()
                    .setValid(false)
                    .setErrorMessage(e.getMessage())
                    .build());                                  // 14
        }
        responseObserver.onCompleted();                         // 15
    }

    @Override
    public void getUserByUsername(                              // 16
            GetUserByUsernameRequest request,
            StreamObserver<GetUserByUsernameResponse> responseObserver) {
        try {
            UserDetails user = userDetailsService
                .loadUserByUsername(request.getUsername());
            List<String> roles = user.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList());
            responseObserver.onNext(
                GetUserByUsernameResponse.newBuilder()
                    .setFound(true)
                    .setUsername(user.getUsername())
                    .addAllRoles(roles)
                    .build());
        } catch (Exception e) {
            responseObserver.onNext(
                GetUserByUsernameResponse.newBuilder()
                    .setFound(false)
                    .build());
        }
        responseObserver.onCompleted();
    }
}
```

### Key Concepts

**Line 1**: `@Service` — registers this class as a Spring bean. Spring gRPC auto-discovers it because it extends `AuthServiceImplBase`.

**Line 2**: `extends AuthServiceGrpc.AuthServiceImplBase` — this is the base class generated from `auth.proto`. It defines the methods you need to override (`validateToken`, `getUserByUsername`). By extending it, you're saying "I am the server implementation of AuthService".

**Line 3**: Same DI as everywhere — `JwtTokenProvider` and `UserDetailsService` are injected via constructor.

**Line 4-6**: The gRPC method signature. Unlike REST controllers that return a value, gRPC uses the **observer pattern**:
- `request` — the incoming protobuf message
- `responseObserver` — a callback object you use to send the response

**Line 5**: `ValidateTokenRequest` is the generated protobuf class. You read fields with getters: `request.getToken()`.

**Line 6**: `StreamObserver<ValidateTokenResponse>` — the "stream observer" is how gRPC handles responses. You call:
- `responseObserver.onNext(response)` — send a response
- `responseObserver.onCompleted()` — signal that you're done

For unary RPCs (one request, one response), you call `onNext()` once, then `onCompleted()`.

**Lines 8-9**: Validation. If the token is empty or invalid, send a response with `valid=false` and an error message. Note that this **does not** use `responseObserver.onError()` — errors are communicated in the response body, so the gRPC status is always `OK`. This is a design choice — see the note at the bottom.

**Lines 10-12**: If the token is valid:
1. Extract the username from the JWT payload
2. Load the full user from the database (to get their roles)
3. Convert authorities to strings: `[ROLE_ADMIN]` → `["ROLE_ADMIN"]`

**Line 13**: Build the success response using protobuf's builder pattern. `addAllRoles(roles)` adds all items from the list to the `repeated` field.

**Line 15**: `responseObserver.onCompleted()` — tells the gRPC framework that the response is complete. The response bytes are sent over the network. **You must always call this** — forgetting it will cause the client to hang forever.

---

## Error Handling Pattern

This service uses the **"errors in response body"** pattern:

```
gRPC Status: always OK
Response body: { valid: false, errorMessage: "..." }
```

This means the gRPC call "succeeds" even when the token is invalid. The caller checks `response.getValid()` to determine success. The alternative pattern (used by MarketGrpcService) throws a gRPC error:

```java
// Alternative pattern (NOT used here)
responseObserver.onError(
    Status.UNAUTHENTICATED
        .withDescription("Invalid token")
        .asRuntimeException());
```

The "errors in response body" approach is simpler for clients — they don't need try-catch blocks.

---

## How Clients Call This Service

In `candilize-market`, the `AuthGrpcClient` calls this service:

```java
public ValidateTokenResponse validateToken(String token) {
    ValidateTokenRequest request = ValidateTokenRequest.newBuilder()
        .setToken(token)
        .build();
    return authServiceStub.validateToken(request);  // Blocking call over gRPC
}
```

The `authServiceStub` is a `BlockingStub` — it sends the request over the network to port 9090 and blocks (waits) until the response arrives.

---

## Testing with grpcurl

```bash
# Validate a JWT token
grpcurl -plaintext \
  -d '{"token": "eyJhbGciOiJIUzI1NiJ9..."}' \
  localhost:9090 com.mohsindev.candilize.proto.auth.AuthService/ValidateToken

# Look up a user
grpcurl -plaintext \
  -d '{"username": "admin"}' \
  localhost:9090 com.mohsindev.candilize.proto.auth.AuthService/GetUserByUsername
```
