# Data Layer — Entities, Repositories & Flyway Migrations

This document covers the database layer: JPA entities (Java classes that map to database tables), Spring Data repositories (interfaces that generate SQL queries), and Flyway migrations (SQL scripts that create and evolve the schema).

---

## How JPA (Java Persistence API) Works

JPA is a specification for mapping Java objects to database tables. **Hibernate** is the implementation (the library that actually talks to MySQL). Spring Data JPA adds a layer on top that auto-generates repository implementations.

```
Your Code → Spring Data JPA → Hibernate (JPA implementation) → JDBC → MySQL
```

You write Java classes with annotations, and Hibernate translates them to SQL:
- `@Entity` class → database table
- `@Column` field → table column
- `@Id` field → primary key
- `repository.save(entity)` → `INSERT INTO ... VALUES (...)` or `UPDATE ... SET ...`
- `repository.findById(id)` → `SELECT * FROM ... WHERE id = ?`

---

## Entities

### UserEntity

**File**: `candilize-auth/.../infrastructure/persistence/entity/UserEntity.java`

```java
@Entity                                                   // 1
@Table(name = "users")                                    // 2
@Getter @Setter                                           // 3
@Builder                                                  // 4
@NoArgsConstructor                                        // 5
@AllArgsConstructor                                       // 6
public class UserEntity {

    @Id                                                   // 7
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // 8
    private Long id;

    @Column(nullable = false, unique = true, length = 50) // 9
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)                          // 10
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    @Builder.Default                                      // 11
    private Boolean enabled = true;

    @Column(name = "created_at", updatable = false)       // 12
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Role {                                    // 13
        ROLE_USER,
        ROLE_ADMIN
    }

    @PrePersist                                           // 14
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate                                            // 15
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

| # | Code | What it does |
|---|---|---|
| 1 | `@Entity` | Marks this class as a JPA entity — Hibernate will manage it and map it to a database table. |
| 2 | `@Table(name = "users")` | Specifies the table name. Without this, Hibernate would use the class name (`UserEntity`) as the table name. |
| 3 | `@Getter @Setter` | Lombok generates `getUsername()`, `setUsername()`, etc. for every field. JPA requires getters/setters. |
| 4 | `@Builder` | Lombok generates a builder class: `UserEntity.builder().username("admin").build()`. |
| 5 | `@NoArgsConstructor` | Lombok generates a no-argument constructor: `new UserEntity()`. **JPA requires this** — Hibernate creates entities by calling the no-arg constructor, then setting fields via setters. |
| 6 | `@AllArgsConstructor` | Lombok generates a constructor with all fields. Required by `@Builder`. |
| 7 | `@Id` | Marks this field as the primary key. Every JPA entity must have an `@Id` field. |
| 8 | `@GeneratedValue(strategy = GenerationType.IDENTITY)` | The database auto-generates the ID (MySQL `AUTO_INCREMENT`). When you save a new entity, the `id` is null — after saving, Hibernate reads the generated ID and sets it on the object. |
| 9 | `@Column(nullable = false, unique = true, length = 50)` | Adds constraints: NOT NULL, UNIQUE, VARCHAR(50). These are used by Hibernate for schema validation (since `ddl-auto=validate`). |
| 10 | `@Enumerated(EnumType.STRING)` | Stores the enum as a string in the database (`"ROLE_USER"`, `"ROLE_ADMIN"`) instead of an integer (0, 1). String storage is safer — if you reorder the enum, the data doesn't break. |
| 11 | `@Builder.Default` | When using the builder, if `enabled` is not set, it defaults to `true`. Without this, the builder would set it to `null`. |
| 12 | `@Column(name = "created_at", updatable = false)` | Maps to the `created_at` column. `updatable = false` means Hibernate will never include this column in UPDATE statements. |
| 13 | `enum Role` | Java enum — a type-safe set of constants. Used instead of raw strings to prevent typos. |
| 14 | `@PrePersist` | JPA lifecycle callback — this method runs automatically before an entity is first saved (INSERT). Sets both timestamps. |
| 15 | `@PreUpdate` | Runs before an UPDATE — updates the `updatedAt` timestamp. |

### SupportedPairEntity

**File**: `candilize-auth/.../infrastructure/persistence/entity/SupportedPairEntity.java`

Same pattern as UserEntity but simpler — represents a trading pair like BTCUSDT.

| Field | Column | Type | Notes |
|---|---|---|---|
| `id` | `id` | BIGINT PK | Auto-increment |
| `symbol` | `symbol` | VARCHAR(20) | e.g., `BTCUSDT`, unique |
| `baseAsset` | `base_asset` | VARCHAR(10) | e.g., `BTC` |
| `quoteAsset` | `quote_asset` | VARCHAR(10) | e.g., `USDT` |
| `enabled` | `enabled` | BOOLEAN | Default true |
| `createdAt` | `created_at` | TIMESTAMP | Set on insert |

### SupportedIntervalEntity

**File**: `candilize-auth/.../infrastructure/persistence/entity/SupportedIntervalEntity.java`

| Field | Column | Type | Notes |
|---|---|---|---|
| `id` | `id` | BIGINT PK | Auto-increment |
| `intervalCode` | `interval_code` | VARCHAR(5) | e.g., `1h`, unique |
| `description` | `description` | VARCHAR(50) | e.g., `1 hour` |
| `enabled` | `enabled` | BOOLEAN | Default true |

---

## Repositories

### How Spring Data JPA Repositories Work

You write a Java **interface** (not a class), extend `JpaRepository`, and Spring **generates the implementation at runtime**. You never write SQL or implement the interface yourself.

```java
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

Spring Data JPA parses the method name and generates SQL:

| Method Name | Generated SQL |
|---|---|
| `findByUsername(String username)` | `SELECT * FROM users WHERE username = ?` |
| `findByEmail(String email)` | `SELECT * FROM users WHERE email = ?` |
| `existsByUsername(String username)` | `SELECT COUNT(*) > 0 FROM users WHERE username = ?` |
| `existsByEmail(String email)` | `SELECT COUNT(*) > 0 FROM users WHERE email = ?` |
| `findAll()` | `SELECT * FROM users` (inherited from JpaRepository) |
| `findById(Long id)` | `SELECT * FROM users WHERE id = ?` (inherited) |
| `save(UserEntity entity)` | `INSERT INTO ...` or `UPDATE ...` (inherited) |
| `deleteById(Long id)` | `DELETE FROM users WHERE id = ?` (inherited) |

### Naming Convention for Query Derivation

| Prefix | Meaning |
|---|---|
| `findBy` | SELECT query, returns entity/list/optional |
| `existsBy` | Returns boolean (true/false) |
| `countBy` | Returns count |
| `deleteBy` | DELETE query |
| `findAllBy` | Returns list |

| Suffix | Meaning |
|---|---|
| `...True` | `= true` |
| `...False` | `= false` |
| `...OrderByXAsc` | `ORDER BY x ASC` |
| `...And...` | Multiple conditions |

### UserRepository

```java
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
```

`JpaRepository<UserEntity, Long>` — the two type parameters are:
1. `UserEntity` — the entity class
2. `Long` — the type of the primary key (`@Id` field)

`Optional<UserEntity>` — Java's way of representing "might be null". Instead of returning `null` when no user is found, it returns `Optional.empty()`. This forces callers to handle the "not found" case explicitly:

```java
UserEntity user = userRepository.findByUsername("admin")
    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
```

### SupportedPairRepository

```java
public interface SupportedPairRepository extends JpaRepository<SupportedPairEntity, Long> {
    List<SupportedPairEntity> findAllByEnabledTrue();
    Optional<SupportedPairEntity> findBySymbol(String symbol);
}
```

`findAllByEnabledTrue()` → `SELECT * FROM supported_pairs WHERE enabled = true`

### SupportedIntervalRepository

```java
public interface SupportedIntervalRepository extends JpaRepository<SupportedIntervalEntity, Long> {
    List<SupportedIntervalEntity> findAllByEnabledTrue();
    Optional<SupportedIntervalEntity> findByIntervalCode(String intervalCode);
}
```

---

## Flyway Migrations

### What is Flyway?

Flyway is a database migration tool. Instead of manually running SQL scripts or letting Hibernate auto-create tables, you write **versioned SQL files** that Flyway runs in order on startup. This ensures every environment (dev, staging, production) has the exact same schema.

### How It Works

1. On startup, Flyway checks the `flyway_schema_history` table in MySQL
2. It compares what migrations have been run vs what `.sql` files exist in `db/migration/`
3. It runs any new migrations in version order
4. Then JPA/Hibernate starts and validates the schema matches the entities

### Migration Files

**V1__init_schema.sql** — creates all three tables:
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('ROLE_USER', 'ROLE_ADMIN') NOT NULL DEFAULT 'ROLE_USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE supported_pairs (...);
CREATE TABLE supported_intervals (...);
```

**V2__seed_pairs_and_intervals.sql** — inserts default data:
- 5 trading pairs: BTCUSDT, ETHUSDT, SOLUSDT, XRPUSDT, ADAUSDT
- 9 intervals: 1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w, 1mo

**V3__seed_admin_user.sql** — creates a default admin user:
```sql
INSERT INTO users (username, email, password, role, enabled)
SELECT 'admin', 'admin@candilize.local',
  '$2a$10$8K1p/a0dL1LXMIgoEDFrwOfMQMIk8PQeLkM5R5r8xWxLzQJKdNkJe',
  'ROLE_ADMIN', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
```

The password hash is for `admin123` — pre-computed with BCrypt. The `WHERE NOT EXISTS` clause prevents duplicate insertion if the migration runs again (idempotent).

### Naming Convention

`V{version}__{description}.sql`
- `V1`, `V2`, `V3` — version numbers (must be sequential)
- `__` — double underscore separator (required by Flyway)
- Description — human-readable (underscores replace spaces)

### FlywayJpaOrderConfig

**File**: `candilize-auth/.../configuration/FlywayJpaOrderConfig.java`

This class solves a timing problem: Hibernate validates the schema (`ddl-auto=validate`) on startup, but Flyway also runs migrations on startup. If Hibernate starts before Flyway, it will see an empty database and fail validation.

```java
@Configuration
public class FlywayJpaOrderConfig implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // Makes entityManagerFactory depend on flywayInitializer
        // → Flyway runs first, THEN Hibernate validates
    }
}
```

`BeanFactoryPostProcessor` runs before any beans are created. It modifies the bean definitions to add a dependency: `entityManagerFactory` depends on `flywayInitializer`. This guarantees Flyway migrations complete before Hibernate starts schema validation.
