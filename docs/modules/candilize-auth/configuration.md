# Configuration — CacheConfig, JacksonConfig, OpenApiConfig

## CacheConfig — Redis Caching

**File**: `candilize-auth/.../configuration/CacheConfig.java`

### What This File Does

Configures Redis as the caching backend. When `@Cacheable` is used on a method (like `ConfigService.getAllPairs()`), the result is stored in Redis and served from cache on subsequent calls until the TTL expires or the cache is evicted.

### Full Source with Commentary

```java
@Configuration                                              // 1
public class CacheConfig {

    @Value("${app.cache.config-ttl:300}")                   // 2
    private long configTtl;

    @Bean                                                    // 3
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory) {      // 4
        RedisCacheConfiguration defaultConfig =
            RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(                          // 5
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(                        // 6
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(
                            new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();                  // 7

        Map<String, RedisCacheConfiguration> cacheConfigurations =
            Map.of(
                "supportedPairs",                            // 8
                    defaultConfig.entryTtl(
                        Duration.ofSeconds(configTtl)),
                "supportedIntervals",
                    defaultConfig.entryTtl(
                        Duration.ofSeconds(configTtl)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(
                cacheConfigurations)                         // 9
            .build();
    }
}
```

| # | What it does |
|---|---|
| 1 | `@Configuration` — this class defines beans |
| 2 | `@Value("${app.cache.config-ttl:300}")` — reads TTL from properties. The `:300` is a default value — if the property isn't set, use 300 seconds (5 minutes). |
| 3 | `@Bean` — the returned `CacheManager` is stored in Spring's IoC container. Spring's caching infrastructure uses this bean. |
| 4 | `RedisConnectionFactory connectionFactory` — Spring auto-creates this bean from `spring.data.redis.host` and `spring.data.redis.port` properties. It's injected as a parameter here. |
| 5 | `serializeKeysWith(StringRedisSerializer)` — cache keys are stored as plain strings in Redis. E.g., key = `supportedPairs::all`. |
| 6 | `serializeValuesWith(GenericJackson2JsonRedisSerializer)` — cache values are stored as JSON in Redis. This uses Jackson to serialize Java objects to JSON and back. |
| 7 | `disableCachingNullValues()` — don't cache null results. If a method returns null, don't store it. |
| 8 | Per-cache configuration — both `supportedPairs` and `supportedIntervals` caches have a TTL of 300 seconds. |
| 9 | `withInitialCacheConfigurations(...)` — registers the per-cache settings. Caches not in this map use `defaultConfig`. |

### How Caching Works in Practice

```
First call to getAllPairs():
  1. Spring checks Redis for key "supportedPairs::all"
  2. Key not found → runs the method → queries MySQL
  3. Stores result in Redis with 300s TTL
  4. Returns result

Second call (within 300s):
  1. Spring checks Redis for key "supportedPairs::all"
  2. Key found → returns cached value immediately
  3. Method body NEVER runs

After addPair() is called:
  1. @CacheEvict(value="supportedPairs", allEntries=true)
  2. Deletes all keys in "supportedPairs" cache
  3. Next getAllPairs() call hits the database again
```

---

## JacksonConfig — JSON Serialization

**File**: `candilize-auth/.../configuration/JacksonConfig.java`

### What This File Does

Configures Jackson — the JSON library that Spring uses to convert Java objects to/from JSON. Every `@RequestBody` and `ResponseEntity` uses Jackson.

```java
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())           // 1
            .disable(SerializationFeature
                .WRITE_DATES_AS_TIMESTAMPS);                // 2
    }
}
```

**Line 1**: `JavaTimeModule` — adds support for Java 8+ date/time types (`Instant`, `LocalDateTime`, `ZonedDateTime`). Without this module, Jackson would fail when trying to serialize these types.

**Line 2**: `WRITE_DATES_AS_TIMESTAMPS` disabled — dates are serialized as ISO-8601 strings (`"2025-01-15T10:30:00"`) instead of numeric timestamps (`1736937000`). The string format is more readable and widely supported by JavaScript frontends.

### Where ObjectMapper is Used

| Class | Usage |
|---|---|
| REST controllers | Automatic: Spring uses it to serialize `ResponseEntity` bodies to JSON |
| `AuthEntryPoint` | Manual: `objectMapper.writeValue(response.getOutputStream(), error)` |
| `InternalApiKeyFilter` | Manual: same pattern for error responses |
| `CacheConfig` | `GenericJackson2JsonRedisSerializer` uses it to serialize cached values |

---

## OpenApiConfig — Swagger UI

**File**: `candilize-auth/.../configuration/OpenApiConfig.java`

### What This File Does

Configures the Swagger UI — a web-based API documentation tool. After starting the app, visit `http://localhost:8081/swagger-ui.html` to see all endpoints, try them out, and see request/response schemas.

```java
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI authOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Candilize Auth API")
                .description("Auth and Config microservice")
                .version("1.0"))
            .addSecurityItem(                                 // 1
                new SecurityRequirement().addList(BEARER_AUTH))
            .components(new Components()
                .addSecuritySchemes(BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")                  // 2
                        .description("Enter JWT from login")));
    }
}
```

**Line 1**: Adds a global security requirement — Swagger UI shows a "lock" icon and an "Authorize" button where you can paste your JWT token.

**Line 2**: Configures the security scheme as Bearer JWT authentication. This tells Swagger UI to send the token in the `Authorization: Bearer <token>` header.

### ExchangeName Enum

**File**: `candilize-auth/.../infrastructure/enums/ExchangeName.java`

```java
public enum ExchangeName {
    MEXC("mexc", "MEXC API"),
    BINANCE("binance", "Binance API");

    @Getter private final String code;
    @Getter private final String name;

    ExchangeName(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
```

A simple enum that defines the supported cryptocurrency exchanges. Each constant has a `code` (used in API requests) and a `name` (human-readable). `@Getter` (Lombok) generates `getCode()` and `getName()` methods.

---

## GlobalExceptionHandler

**File**: `candilize-auth/.../api/exception/GlobalExceptionHandler.java`

### What This File Does

A centralized error handler for all REST controllers. When any controller throws an exception, this class catches it and returns a structured JSON error response.

```java
@Slf4j
@RestControllerAdvice                                        // 1
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)         // 2
    public ResponseEntity<ErrorResponse>
            handleAuthenticationException(...) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(buildError(401, "Unauthorized", ...));
    }

    @ExceptionHandler(AccessDeniedException.class)           // 3
    public ResponseEntity<ErrorResponse>
            handleAccessDeniedException(...) { ... }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse>
            handleEntityNotFoundException(...) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class) // 4
    public ResponseEntity<ErrorResponse>
            handleValidationException(...) {
        String message = ex.getBindingResult().getFieldErrors()
            .stream().map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));               // 5
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(buildError(400, "Bad Request", message, ...));
    }

    @ExceptionHandler(Exception.class)                       // 6
    public ResponseEntity<ErrorResponse>
            handleGenericException(...) { ... }
}
```

**Line 1**: `@RestControllerAdvice` — a Spring component that applies to all controllers. Methods annotated with `@ExceptionHandler` catch exceptions thrown by any controller method.

**Line 2**: Catches `AuthenticationException` (bad credentials) → HTTP 401.

**Line 3**: Catches `AccessDeniedException` (wrong role) → HTTP 403.

**Line 4**: Catches `MethodArgumentNotValidException` — thrown when `@Valid` fails (e.g., blank username).

**Line 5**: Extracts all validation error messages and joins them with `"; "`. Example: `"Username is required; Password must be at least 8 characters"`.

**Line 6**: Catches all other exceptions → HTTP 500. This is the "catch-all" — it prevents stack traces from leaking to API clients.

### ErrorResponse DTO

```java
@Data @Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private String timestamp;
}
```

Example JSON response:
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Bad credentials",
  "path": "/api/v1/auth/login",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

`@Data` (Lombok) generates getters, setters, `equals()`, `hashCode()`, and `toString()`.
`@Builder` generates the builder pattern used in `buildError(...)`.
