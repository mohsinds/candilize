# candilize-proto Module

## What This Module Does

This module contains **Protocol Buffer (protobuf) definitions** — `.proto` files that define the contract for gRPC communication between microservices. It does not contain any business logic. It is a **shared library** that other modules depend on.

When you run `mvn compile`, this module takes the `.proto` files and generates Java classes (source code) that other modules use to send and receive gRPC messages.

---

## How gRPC and Protobuf Work (Beginner Explanation)

### The Problem
When two services need to talk to each other over a network, they need to agree on:
1. **What methods can be called** (like function names)
2. **What data to send** (like function parameters)
3. **What data comes back** (like return values)

### The Solution: Protocol Buffers
Protocol Buffers (protobuf) is a language created by Google for defining these contracts. You write a `.proto` file that says:
- "There is a service called `AuthService`"
- "It has a method called `ValidateToken`"
- "You send it a `ValidateTokenRequest` containing a `token` string"
- "It returns a `ValidateTokenResponse` containing `valid`, `username`, and `roles`"

Then a **code generator** (`protoc`) reads this `.proto` file and generates Java classes automatically. These generated classes handle all the networking, serialization (converting objects to bytes), and deserialization (converting bytes back to objects).

### Why Not Just Use REST?
gRPC is faster than REST for service-to-service communication because:
- It uses **binary format** (protobuf) instead of text (JSON) — smaller payload, faster to parse
- It uses **HTTP/2** which supports multiplexing (multiple requests over one connection)
- The **contract is strict** — both sides know exactly what to expect at compile time

In Candilize, REST is used for external clients (browser, mobile app), while gRPC is used internally between microservices.

---

## Proto Files

### auth.proto

**Location**: `candilize-proto/src/main/proto/auth.proto`

```protobuf
syntax = "proto3";                                          // 1

package com.mohsindev.candilize.proto.auth;                 // 2

option java_multiple_files = true;                          // 3
option java_package = "com.mohsindev.candilize.proto.auth"; // 4
option java_outer_classname = "AuthProto";                  // 5

service AuthService {                                       // 6
  rpc ValidateToken(ValidateTokenRequest)                   // 7
      returns (ValidateTokenResponse);
  rpc GetUserByUsername(GetUserByUsernameRequest)            // 8
      returns (GetUserByUsernameResponse);
}

message ValidateTokenRequest {                              // 9
  string token = 1;                                         // 10
}

message ValidateTokenResponse {                             // 11
  bool valid = 1;
  string username = 2;
  repeated string roles = 3;                                // 12
  string error_message = 4;
}

message GetUserByUsernameRequest {                          // 13
  string username = 1;
}

message GetUserByUsernameResponse {
  bool found = 1;
  string username = 2;
  repeated string roles = 3;
}
```

**Line-by-line:**

| Line | Code | Explanation |
|------|------|-------------|
| 1 | `syntax = "proto3"` | Use Protocol Buffers version 3 (latest). Version 2 has different rules about required fields. |
| 2 | `package com.mohsindev...` | Logical namespace for this proto file. Prevents name collisions if two protos define a message with the same name. |
| 3 | `java_multiple_files = true` | Generate each message and service as its own `.java` file instead of nesting them all inside one outer class. |
| 4 | `java_package = "..."` | The Java package for generated classes. This is where `ValidateTokenRequest.java`, etc. end up. |
| 5 | `java_outer_classname = "AuthProto"` | If `java_multiple_files` were false, all classes would be nested inside `AuthProto.java`. Since it's true, this class still exists but is mostly empty. |
| 6 | `service AuthService` | Defines a gRPC service — like a Java interface with methods that can be called over the network. |
| 7 | `rpc ValidateToken(...)` | A remote method. Takes a `ValidateTokenRequest`, returns a `ValidateTokenResponse`. This is a **unary RPC** (one request, one response — like a normal function call). |
| 8 | `rpc GetUserByUsername(...)` | Another unary RPC — looks up a user by username. |
| 9 | `message ValidateTokenRequest` | A message is like a Java class with fields. This one has a single field: `token`. |
| 10 | `string token = 1` | The `= 1` is a **field number**, not a default value. Protobuf uses field numbers (not names) in the binary encoding. This makes it efficient and allows adding new fields without breaking old code. |
| 11 | `message ValidateTokenResponse` | The response message. Contains whether the token is valid, the username, roles, and an error message. |
| 12 | `repeated string roles = 3` | `repeated` means this field is a list (like `List<String>` in Java). A user can have multiple roles. |
| 13 | `message GetUserByUsernameRequest` | Request to look up a user by username. Used internally by auth service. |

**Who uses this:**
- **Server** (candilize-auth): Implements `AuthServiceImplBase` — provides the actual logic for `ValidateToken` and `GetUserByUsername`
- **Clients** (candilize-market, candilize-technical): Use `AuthServiceGrpc.AuthServiceBlockingStub` to call these methods

### market.proto

**Location**: `candilize-proto/src/main/proto/market.proto`

```protobuf
syntax = "proto3";

package com.mohsindev.candilize.proto.market;

option java_multiple_files = true;
option java_package = "com.mohsindev.candilize.proto.market";
option java_outer_classname = "MarketProto";

service MarketService {
  rpc GetCandles(GetCandlesRequest) returns (GetCandlesResponse);  // 1
}

message GetCandlesRequest {
  string pair = 1;              // e.g., "BTCUSDT"
  string interval_code = 2;     // e.g., "1h"
  int32 limit = 3;              // max number of candles to return
  int64 start_time = 4;         // Unix timestamp (milliseconds)
  int64 end_time = 5;           // Unix timestamp (milliseconds)
  string exchange = 6;          // e.g., "binance"
}

message GetCandlesResponse {
  repeated Candle candles = 1;  // List of candle data               // 2
}

message Candle {
  string symbol = 1;            // e.g., "BTCUSDT"
  string interval_code = 2;     // e.g., "1h"
  int64 open_time = 3;          // When this candle period started
  string open_price = 4;        // Prices are strings to avoid floating-point issues
  string high_price = 5;
  string low_price = 6;
  string close_price = 7;
  string volume = 8;
  int64 close_time = 9;         // When this candle period ended
  string exchange = 10;         // Which exchange the data came from
}
```

**Key points:**
1. `GetCandles` is the only RPC — it fetches historical OHLCV (Open, High, Low, Close, Volume) candle data for trading analysis.
2. Prices are `string` not `double` or `float`. This is deliberate — floating-point numbers have precision issues (e.g., `0.1 + 0.2 = 0.30000000000000004`). Financial data uses strings or `BigDecimal` to avoid this.

**Who uses this:**
- **Server** (candilize-market): Implements `MarketServiceImplBase` — queries MongoDB for candle data
- **Client** (candilize-technical): Uses `MarketServiceGrpc.MarketServiceBlockingStub` to fetch candles for technical analysis

---

## What Gets Generated

When you run `mvn compile` in this module, the protobuf-maven-plugin generates these Java classes:

### From auth.proto
| Generated Class | Type | Purpose |
|---|---|---|
| `ValidateTokenRequest` | Message class | Builder pattern to create requests |
| `ValidateTokenResponse` | Message class | Builder pattern to create responses |
| `GetUserByUsernameRequest` | Message class | Request for user lookup |
| `GetUserByUsernameResponse` | Message class | Response for user lookup |
| `AuthServiceGrpc` | Service stubs | Contains `AuthServiceImplBase` (server) and `AuthServiceBlockingStub` (client) |

### From market.proto
| Generated Class | Type | Purpose |
|---|---|---|
| `GetCandlesRequest` | Message class | Builder pattern to create candle requests |
| `GetCandlesResponse` | Message class | Contains list of candles |
| `Candle` | Message class | Single candle data point |
| `MarketServiceGrpc` | Service stubs | Contains `MarketServiceImplBase` and `MarketServiceBlockingStub` |

Generated files go to `target/generated-sources/protobuf/java/` and `target/generated-sources/protobuf/grpc-java/`.

### Using Generated Classes (Example)

**Creating a request (builder pattern):**
```java
ValidateTokenRequest request = ValidateTokenRequest.newBuilder()
        .setToken("eyJhbGciOiJIUzI1NiJ9...")
        .build();
```

**Reading a response:**
```java
ValidateTokenResponse response = stub.validateToken(request);
boolean isValid = response.getValid();
String username = response.getUsername();
List<String> roles = response.getRolesList();
```

Protobuf uses the **builder pattern** — you create objects with `.newBuilder()`, set fields with `.setXxx()`, and finalize with `.build()`. Objects are immutable after building.

---

## pom.xml Explained

```xml
<properties>
    <protobuf.version>3.25.1</protobuf.version>     <!-- protobuf library version -->
    <grpc.version>1.60.1</grpc.version>              <!-- gRPC framework version -->
    <protobuf-plugin.version>0.6.1</protobuf-plugin.version>  <!-- Maven plugin version -->
</properties>
```

### Dependencies

| Dependency | Purpose |
|---|---|
| `grpc-stub` | Base classes for gRPC client stubs (`BlockingStub`, `FutureStub`) |
| `grpc-protobuf` | Integration between gRPC and protobuf — serialization/deserialization |
| `javax.annotation-api` | Provides `@Generated` annotation used by generated code |

### Build Plugins

| Plugin | Purpose |
|---|---|
| `os-maven-plugin` | Detects your OS (mac, linux, windows) so the right `protoc` binary is downloaded |
| `protobuf-maven-plugin` | Runs `protoc` compiler during `mvn compile` to generate Java from `.proto` files |
| `build-helper-maven-plugin` | Tells Maven where to find the generated source files so they compile correctly |

The `protobuf-maven-plugin` has two executions:
1. `compile` — generates message classes (the `ValidateTokenRequest`, etc.)
2. `compile-custom` with `grpc-java` plugin — generates service stubs (the `AuthServiceGrpc`, etc.)

---

## Communication Matrix

```
candilize-auth (gRPC server :9090)
    └── AuthService
        ├── ValidateToken      ← called by market, technical
        └── GetUserByUsername   ← called by auth internally

candilize-market (gRPC server :9091, gRPC client)
    ├── MarketService (server)
    │   └── GetCandles         ← called by technical
    └── AuthService (client)
        └── ValidateToken      → calls auth:9090

candilize-technical (gRPC client only, no server)
    ├── AuthService (client)
    │   └── ValidateToken      → calls auth:9090
    └── MarketService (client)
        └── GetCandles         → calls market:9091
```

---

## FAQ

**Q: Do I need to regenerate code when I change a `.proto` file?**
A: Yes. Run `mvn compile` in the `candilize-proto` module. Then rebuild the dependent modules.

**Q: Where are the generated files?**
A: `candilize-proto/target/generated-sources/protobuf/`. These are NOT committed to git — they're regenerated on every build.

**Q: Can I add a new RPC method?**
A: Yes. Add it to the `.proto` file, run `mvn compile`, then implement the method in the server module and call it from the client module.

**Q: Why are field numbers important?**
A: Protobuf encodes data using field numbers, not field names. If you change a field number, old clients/servers won't understand new messages. Never reuse or change existing field numbers — only add new ones.
