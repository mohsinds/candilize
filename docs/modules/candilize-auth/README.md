# candilize-auth Module

## What This Module Does

The **auth module** is the identity and configuration service for Candilize. It handles:

1. **User authentication** — register, login, JWT token issuance and refresh
2. **Configuration management** — CRUD for trading pairs (BTCUSDT, ETHUSDT, etc.) and candle intervals (1m, 5m, 1h, etc.)
3. **Internal API** — provides scheduler configuration to the market service via API-key-secured endpoints
4. **gRPC token validation** — other services call this module over gRPC to validate JWT tokens on every HTTP request

## Tech Stack

| Technology | Purpose |
|---|---|
| Spring Boot 4.0.2 | Application framework |
| Spring Security | Authentication and authorization |
| Spring Data JPA + Hibernate | ORM for MySQL database |
| Flyway | Database schema migration |
| MySQL | Relational database for users, pairs, intervals |
| Redis | Cache for config data (pairs, intervals) |
| JJWT (io.jsonwebtoken) | JWT token generation and validation |
| Spring gRPC | gRPC server for token validation |
| Spring Boot Actuator | Health checks, metrics |
| SpringDoc OpenAPI | Swagger UI documentation |
| Lombok | Boilerplate code reduction |

## Architecture Overview

```
HTTP :8081                                   gRPC :9090
┌─────────────────────────────────────┐     ┌────────────────────────┐
│ REST Controllers                    │     │ AuthGrpcService        │
│  ├── AuthController    /auth/**     │     │  ├── ValidateToken     │
│  ├── ConfigController  /config/**   │     │  └── GetUserByUsername │
│  └── InternalConfig    /internal/** │     └────────────────────────┘
├─────────────────────────────────────┤                │
│ Security Filters                    │                │
│  ├── InternalApiKeyFilter           │                │
│  ├── JwtAuthenticationFilter        │                │
│  └── AuthEntryPoint                 │                │
├─────────────────────────────────────┤                │
│ Services                            │                │
│  ├── AuthService (register/login)   │◄───────────────┘
│  └── ConfigService (pairs/intervals)│
├─────────────────────────────────────┤
│ Data Layer                          │
│  ├── UserEntity → UserRepository    │──── MySQL (candilize1)
│  ├── SupportedPairEntity → Repo     │
│  └── SupportedIntervalEntity → Repo │
├─────────────────────────────────────┤
│ Config                              │
│  ├── SecurityConfig                 │
│  ├── CacheConfig → Redis            │──── Redis (:6379)
│  ├── FlywayJpaOrderConfig           │
│  ├── JacksonConfig                  │
│  └── OpenApiConfig                  │
└─────────────────────────────────────┘
```

## Entry Point

**File**: `CandilizeAuthApplication.java`

```java
@EnableCaching           // 1
@SpringBootApplication   // 2
public class CandilizeAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(CandilizeAuthApplication.class, args);  // 3
    }
}
```

| Line | What it does |
|---|---|
| 1 | `@EnableCaching` — activates Spring's caching infrastructure. Without this, `@Cacheable` and `@CacheEvict` annotations on `ConfigService` would be ignored. |
| 2 | `@SpringBootApplication` — combines three annotations: `@Configuration` (this class can define beans), `@EnableAutoConfiguration` (Spring Boot auto-configures based on dependencies), `@ComponentScan` (scans this package and sub-packages for `@Component`, `@Service`, `@Controller`, etc.). |
| 3 | `SpringApplication.run(...)` — boots the application: creates the Spring IoC container, scans for beans, auto-configures everything, starts the embedded Tomcat server on port 8081, starts the gRPC server on port 9090. |

## REST API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | Register a new user |
| POST | `/api/v1/auth/login` | Public | Login, get JWT tokens |
| POST | `/api/v1/auth/refresh` | Public | Refresh expired access token |
| GET | `/api/v1/config/pairs` | JWT + ADMIN | List all trading pairs |
| POST | `/api/v1/config/pairs` | JWT + ADMIN | Add a trading pair |
| PUT | `/api/v1/config/pairs/{id}` | JWT + ADMIN | Enable/disable a pair |
| DELETE | `/api/v1/config/pairs/{id}` | JWT + ADMIN | Delete a pair |
| GET | `/api/v1/config/intervals` | JWT + ADMIN | List all intervals |
| PUT | `/api/v1/config/intervals/{id}` | JWT + ADMIN | Enable/disable an interval |
| GET | `/api/v1/config/exchanges` | JWT + ADMIN | List available exchanges |
| GET | `/api/v1/internal/scheduler-config` | X-API-Key | Get enabled pairs/intervals for scheduler |

## gRPC Endpoints

| Service | Method | Purpose |
|---|---|---|
| `AuthService` | `ValidateToken` | Validates a JWT token, returns username and roles |
| `AuthService` | `GetUserByUsername` | Looks up a user by username |

## Database Schema

Three tables in MySQL database `candilize1`:

### users
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Auto-increment |
| username | VARCHAR(50) | Unique |
| email | VARCHAR(100) | Unique |
| password | VARCHAR(255) | BCrypt hashed |
| role | ENUM | `ROLE_USER` or `ROLE_ADMIN` |
| enabled | BOOLEAN | Account active flag |
| created_at | TIMESTAMP | Set on insert |
| updated_at | TIMESTAMP | Updated on modify |

### supported_pairs
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Auto-increment |
| symbol | VARCHAR(20) | e.g., `BTCUSDT`, unique |
| base_asset | VARCHAR(10) | e.g., `BTC` |
| quote_asset | VARCHAR(10) | e.g., `USDT` |
| enabled | BOOLEAN | Whether scheduler should fetch this pair |
| created_at | TIMESTAMP | |

### supported_intervals
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Auto-increment |
| interval_code | VARCHAR(5) | e.g., `1h`, unique |
| description | VARCHAR(50) | e.g., `1 hour` |
| enabled | BOOLEAN | Whether scheduler should use this interval |

## Configuration (application.properties)

| Property | Value | Purpose |
|---|---|---|
| `server.port` | 8081 | HTTP port for REST API |
| `spring.grpc.server.port` | 9090 | gRPC server port |
| `spring.datasource.url` | `jdbc:mysql://...` | MySQL connection |
| `spring.jpa.hibernate.ddl-auto` | validate | Only validate schema, don't modify (Flyway handles migrations) |
| `spring.flyway.enabled` | true | Run SQL migrations on startup |
| `spring.data.redis.host` | localhost | Redis for caching |
| `app.jwt.secret` | Base64 key | HMAC secret for JWT signing |
| `app.jwt.access-token-expiration` | 900000 | 15 minutes in milliseconds |
| `app.jwt.refresh-token-expiration` | 604800000 | 7 days in milliseconds |
| `app.internal.api-key` | `internal-dev-key` | API key for internal endpoints |

## File Reference

### Controllers
- [auth-controller.md](auth-controller.md) — AuthController (register, login, refresh)
- [config-controller.md](config-controller.md) — ConfigController (pairs, intervals, exchanges)
- [internal-config-controller.md](internal-config-controller.md) — InternalConfigController (scheduler config)

### gRPC
- [auth-grpc-service.md](auth-grpc-service.md) — AuthGrpcService (token validation, user lookup)

### Security
- [security.md](security.md) — SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter, InternalApiKeyFilter, AuthEntryPoint, UserDetailsServiceImpl

### Services
- [services.md](services.md) — AuthService, ConfigService

### Data Layer
- [data-layer.md](data-layer.md) — Entities, repositories, Flyway migrations

### Configuration
- [configuration.md](configuration.md) — CacheConfig, FlywayJpaOrderConfig, JacksonConfig, OpenApiConfig
