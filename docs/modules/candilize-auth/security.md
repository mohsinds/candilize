# Security Layer — JWT, API Keys, Filters & Configuration

This document covers all security-related classes in the auth module. Security is the most complex part of a Spring Boot application, so this document explains every piece in detail.

---

## How Spring Security Works (Beginner Overview)

Every HTTP request passes through a **filter chain** before reaching your controller. Think of it like airport security — each filter checks something and either lets the request through or rejects it.

```
HTTP Request
    │
    ▼
┌─────────────────────────────┐
│ InternalApiKeyFilter        │  ← Checks X-API-Key for /internal/** paths
│ (skips non-internal paths)  │
├─────────────────────────────┤
│ JwtAuthenticationFilter     │  ← Extracts JWT from Authorization header
│ (sets SecurityContext)      │
├─────────────────────────────┤
│ Spring Security's built-in  │  ← Checks if the authenticated user has
│ authorization filters       │     the required role for the URL
├─────────────────────────────┤
│ ExceptionHandling           │  ← If auth fails, AuthEntryPoint returns 401
└─────────────────────────────┘
    │
    ▼
Controller method
```

Spring Security uses a concept called `SecurityContext` — a thread-local storage that holds the currently authenticated user. Filters set it, and later code (controllers, services) can read it.

---

## SecurityConfig.java

**File**: `candilize-auth/src/main/java/com/mohsindev/candilize/auth/security/SecurityConfig.java`

This is the central security configuration. It defines which URLs require authentication, which roles are needed, and how the filter chain is assembled.

```java
@Configuration                    // 1
@EnableWebSecurity                // 2
@EnableMethodSecurity             // 3
@RequiredArgsConstructor          // 4
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;   // 5
    private final InternalApiKeyFilter internalApiKeyFilter;
    private final AuthEntryPoint authEntryPoint;

    @Bean                                                            // 6
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)                   // 7
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))  // 8
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 9
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(authEntryPoint))   // 10
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()       // 11
                .requestMatchers("/api/v1/internal/**")
                    .hasAuthority("ROLE_SERVICE")                     // 12
                .requestMatchers("/actuator/**").permitAll()          // 13
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                    "/v3/api-docs/**", "/swagger-resources/**")
                    .permitAll()                                     // 14
                .requestMatchers("/api/v1/config/**")
                    .hasRole("ADMIN")                                // 15
                .anyRequest().authenticated())                       // 16
            .addFilterBefore(internalApiKeyFilter,
                UsernamePasswordAuthenticationFilter.class)          // 17
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);         // 18

        return http.build();                                         // 19
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() { ... } // 20

    @Bean
    public PasswordEncoder passwordEncoder() {                       // 21
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(              // 22
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### Annotations

| # | Annotation | What it does |
|---|---|---|
| 1 | `@Configuration` | Marks this class as a source of Spring beans. Methods annotated with `@Bean` inside this class will be called by Spring to create objects and register them in the IoC container. |
| 2 | `@EnableWebSecurity` | Activates Spring Security's web security support. Without this, none of the security configuration would take effect. |
| 3 | `@EnableMethodSecurity` | Enables `@PreAuthorize` and `@PostAuthorize` annotations on controller methods. `ConfigController` uses `@PreAuthorize("hasRole('ADMIN')")`. |
| 4 | `@RequiredArgsConstructor` | Lombok generates a constructor injecting all `final` fields (the three filters/entry point). |

### DI (Line 5)

```java
private final JwtAuthenticationFilter jwtAuthenticationFilter;
private final InternalApiKeyFilter internalApiKeyFilter;
private final AuthEntryPoint authEntryPoint;
```

Spring injects these three beans via constructor injection. All three are `@Component` classes — Spring finds them during component scanning and creates them before this config class.

### The SecurityFilterChain (Lines 6-19)

Line 6: `@Bean` means Spring will call this method and store the returned object in its IoC container. Other parts of Spring Security will use this `SecurityFilterChain` bean.

Line 7: **CSRF disabled**. CSRF (Cross-Site Request Forgery) protection is for browser-based apps with cookies. Since this API uses JWT tokens in the `Authorization` header, CSRF is not needed. `AbstractHttpConfigurer::disable` is a method reference — shorthand for `csrf -> csrf.disable()`.

Line 8: **CORS enabled**. CORS (Cross-Origin Resource Sharing) allows the frontend (React app on a different port) to call this API. The configuration allows all origins, methods, and headers.

Line 9: **Stateless sessions**. `STATELESS` means Spring will NOT create an HTTP session. Every request must carry its own authentication (JWT token). This is standard for REST APIs — the server doesn't remember who you are between requests.

Line 10: **Custom entry point**. When an unauthenticated request hits a protected endpoint, `AuthEntryPoint` handles the error response (returns JSON instead of HTML redirect).

Lines 11-16: **URL authorization rules**. These are evaluated in order — first match wins:

| Rule | URLs | Access |
|---|---|---|
| Line 11 | `/api/v1/auth/**` | Anyone (public) — register, login, refresh |
| Line 12 | `/api/v1/internal/**` | Must have `ROLE_SERVICE` authority (API key) |
| Line 13 | `/actuator/**` | Anyone — health checks, metrics |
| Line 14 | `/swagger-ui/**`, `/v3/api-docs/**` | Anyone — API documentation |
| Line 15 | `/api/v1/config/**` | Must have `ROLE_ADMIN` |
| Line 16 | Everything else | Must be authenticated (any role) |

**Note**: `hasAuthority("ROLE_SERVICE")` vs `hasRole("ADMIN")`. `hasRole("ADMIN")` automatically prepends `ROLE_`, so it checks for `ROLE_ADMIN`. `hasAuthority("ROLE_SERVICE")` checks the exact string.

Lines 17-18: **Filter ordering**. `addFilterBefore(X, UsernamePasswordAuthenticationFilter.class)` means "insert X before Spring's built-in username/password filter". Both custom filters run before Spring's default authentication.

### Beans (Lines 20-22)

Line 21: `PasswordEncoder` bean returns `BCryptPasswordEncoder`. BCrypt is a one-way hashing algorithm designed for passwords. When a user registers, their password is hashed with BCrypt. When they login, the submitted password is hashed and compared to the stored hash. BCrypt includes a random salt, so the same password produces different hashes each time.

Line 22: `AuthenticationManager` bean. Spring Security provides this — it orchestrates authentication by delegating to `UserDetailsService` and `PasswordEncoder`. When `AuthService.login()` calls `authenticationManager.authenticate(...)`, the manager:
1. Calls `UserDetailsServiceImpl.loadUserByUsername()` to get the stored user
2. Uses `BCryptPasswordEncoder.matches()` to compare passwords
3. Throws `BadCredentialsException` if they don't match

---

## JwtTokenProvider.java

**File**: `candilize-auth/src/main/java/com/mohsindev/candilize/auth/security/JwtTokenProvider.java`

This class creates and validates JWT tokens. JWT (JSON Web Token) is a standard for encoding claims (user identity, roles, expiration) into a signed string.

```java
@Slf4j
@Component                                                    // 1
public class JwtTokenProvider {

    private final SecretKey secretKey;                         // 2
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,        // 3
            @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(                   // 4
            java.util.Base64.getDecoder().decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
```

### How DI Works Here

This uses **constructor injection with `@Value`**. Unlike `@RequiredArgsConstructor` (which injects beans), `@Value` injects values from `application.properties`.

When Spring creates `JwtTokenProvider`:
1. It reads `app.jwt.secret` from properties → `"dGhpcyBpcyBhIHNlY3JldCBrZXkgZm9yIGRldmVsb3BtZW50IG9ubHk="`
2. It reads `app.jwt.access-token-expiration` → `900000` (15 min in ms)
3. It reads `app.jwt.refresh-token-expiration` → `604800000` (7 days in ms)
4. It passes all three to the constructor

Line 4: `Keys.hmacShaKeyFor(...)` converts the Base64 secret string into a `SecretKey` object used for HMAC-SHA signing. The secret is decoded from Base64, then wrapped in a key object.

### Token Generation

```java
public String generateAccessToken(UserDetails userDetails) {
    return Jwts.builder()
        .subject(userDetails.getUsername())                    // 1
        .claim("roles", userDetails.getAuthorities()          // 2
            .stream().map(a -> a.getAuthority()).toList())
        .issuedAt(new Date())                                 // 3
        .expiration(new Date(
            System.currentTimeMillis() + accessTokenExpiration))  // 4
        .signWith(secretKey)                                  // 5
        .compact();                                           // 6
}
```

| Line | What it does |
|---|---|
| 1 | Sets the `sub` (subject) claim — the username |
| 2 | Adds a custom `roles` claim — list of authorities like `["ROLE_ADMIN"]` |
| 3 | Sets the `iat` (issued at) claim — current timestamp |
| 4 | Sets the `exp` (expiration) claim — current time + 15 minutes |
| 5 | Signs the token with HMAC-SHA using the secret key |
| 6 | Builds the final JWT string (base64url-encoded header.payload.signature) |

A JWT looks like: `eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.signature`
- Part 1: Header (algorithm)
- Part 2: Payload (claims — username, roles, expiration)
- Part 3: Signature (proves the token wasn't tampered with)

### Token Validation

```java
public boolean validateToken(String token) {
    try {
        Jwts.parser()
            .verifyWith(secretKey)          // Use same key to verify signature
            .build()
            .parseSignedClaims(token);      // Parse and verify — throws if invalid
        return true;
    } catch (JwtException | IllegalArgumentException e) {
        log.debug("Invalid JWT: {}", e.getMessage());
        return false;
    }
}
```

`parseSignedClaims()` does three things:
1. Decodes the Base64 token
2. Verifies the HMAC signature (ensures token wasn't modified)
3. Checks the expiration date (rejects expired tokens)

If any check fails, it throws a `JwtException`.

---

## JwtAuthenticationFilter.java

**File**: `candilize-auth/src/main/java/com/mohsindev/candilize/auth/security/JwtAuthenticationFilter.java`

This filter runs on every HTTP request. It extracts the JWT from the `Authorization` header and sets the user's identity in Spring's `SecurityContext`.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {  // 1

    private final JwtTokenProvider jwtTokenProvider;                  // 2
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,                              // 3
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);             // 4

            if (StringUtils.hasText(jwt)                             // 5
                    && jwtTokenProvider.validateToken(jwt)) {
                String username = jwtTokenProvider
                    .getUsernameFromToken(jwt);                      // 6
                UserDetails userDetails = userDetailsService
                    .loadUserByUsername(username);                    // 7

                UsernamePasswordAuthenticationToken authentication = // 8
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(                           // 9
                    new WebAuthenticationDetailsSource()
                        .buildDetails(request));
                SecurityContextHolder.getContext()
                    .setAuthentication(authentication);              // 10
            }
        } catch (Exception ex) {
            log.debug("Cannot set user authentication: {}",
                ex.getMessage());                                    // 11
        }

        filterChain.doFilter(request, response);                    // 12
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");    // 13
        if (StringUtils.hasText(bearerToken)
                && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);                        // 14
        }
        return null;
    }
}
```

### Key Concepts

**Line 1**: `OncePerRequestFilter` is a Spring base class that guarantees the filter runs exactly once per request (even if the request is forwarded internally). All custom security filters should extend this.

**Lines 4-14**: The JWT extraction flow:
1. Get the `Authorization` header from the HTTP request
2. Check it starts with `"Bearer "` (note the space)
3. Strip the `"Bearer "` prefix to get the raw token
4. Validate the token (signature + expiration)
5. Extract the username from the token's `sub` claim
6. Load the full user details from the database
7. Create an `Authentication` object with the user's authorities
8. Store it in `SecurityContextHolder` — now Spring Security knows who this user is

**Line 10**: `SecurityContextHolder.getContext().setAuthentication(authentication)` — this is the critical line. It sets the authenticated user for the current request thread. After this line:
- `@PreAuthorize("hasRole('ADMIN')")` can check the user's roles
- `SecurityContextHolder.getContext().getAuthentication()` returns the user info
- Spring Security's authorization filters allow/deny access based on this

**Line 12**: `filterChain.doFilter(request, response)` — passes the request to the next filter in the chain. This must always be called, even if authentication fails (Spring's authorization filter will handle the rejection).

---

## InternalApiKeyFilter.java

**File**: `candilize-auth/src/main/java/com/mohsindev/candilize/auth/security/InternalApiKeyFilter.java`

This filter secures the `/api/v1/internal/**` endpoints with an API key. The market service calls these endpoints to fetch scheduler configuration.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${app.internal.api-key}")                         // 1
    private String apiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI()
            .startsWith("/api/v1/internal/");                 // 2
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader("X-API-Key");          // 3
        if (key != null && key.equals(apiKey)) {
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                    "internal-service", null,
                    List.of(new SimpleGrantedAuthority(
                        "ROLE_SERVICE")));                    // 4
            SecurityContextHolder.getContext()
                .setAuthentication(auth);
            filterChain.doFilter(request, response);          // 5
        } else {
            log.warn("Invalid or missing X-API-Key: {}",
                request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.builder()
                    .status(401)
                    .error("Unauthorized")
                    .message("Invalid or missing X-API-Key")
                    .path(request.getRequestURI())
                    .timestamp(Instant.now().toString())
                    .build());                                // 6
        }
    }
}
```

**Line 1**: `@Value` injects the API key from `application.properties`. In dev, it's `"internal-dev-key"`.

**Line 2**: `shouldNotFilter()` — if this returns `true`, the filter is skipped entirely. This filter only runs for `/api/v1/internal/` paths. For all other URLs, it does nothing.

**Line 3**: Reads the `X-API-Key` HTTP header from the request.

**Line 4**: If the key matches, creates a synthetic authentication with `ROLE_SERVICE` authority. The `"internal-service"` string is the principal (username) — it's a placeholder since there's no real user. This authentication satisfies the security rule `.requestMatchers("/api/v1/internal/**").hasAuthority("ROLE_SERVICE")`.

**Line 6**: If the key is wrong or missing, writes a JSON error response directly to the output stream (bypassing controllers).

---

## AuthEntryPoint.java

**File**: `candilize-auth/src/main/java/com/mohsindev/candilize/auth/security/AuthEntryPoint.java`

This class handles authentication failures — when a request reaches a protected endpoint without valid credentials.

```java
@Component
@RequiredArgsConstructor
public class AuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        ErrorResponse error = ErrorResponse.builder()
            .status(401)
            .error("Unauthorized")
            .message("Invalid or expired JWT token")
            .path(request.getRequestURI())
            .timestamp(Instant.now().toString())
            .build();
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
```

Without this, Spring Security would return an HTML login page (default behavior). This class returns a JSON error response instead, which is what API clients expect.

---

## UserDetailsServiceImpl.java

**File**: `candilize-auth/src/main/java/com/mohsindev/candilize/auth/security/UserDetailsServiceImpl.java`

This class connects Spring Security to the database. Spring Security doesn't know how to load users from your specific database — you tell it by implementing `UserDetailsService`.

```java
@Service                                                     // 1
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {  // 2

    private final UserRepository userRepository;              // 3

    @Override
    @Transactional(readOnly = true)                           // 4
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)  // 5
            .orElseThrow(() -> new UsernameNotFoundException(
                "User not found: " + username));
        return User.builder()                                 // 6
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(Collections.singletonList(
                new SimpleGrantedAuthority(
                    user.getRole().name())))                   // 7
            .disabled(!user.getEnabled())                     // 8
            .build();
    }
}
```

**Line 1**: `@Service` — same as `@Component`, but semantically indicates this is a service-layer bean.

**Line 2**: `implements UserDetailsService` — this is a Spring Security interface with one method: `loadUserByUsername`. Spring Security calls this whenever it needs to authenticate a user (during login) or load user details (during JWT validation).

**Line 3**: `UserRepository` is injected — a Spring Data JPA interface that generates SQL queries automatically.

**Line 4**: `@Transactional(readOnly = true)` — opens a read-only database transaction. This is important because `findByUsername` executes a SQL query, and all JPA operations must run within a transaction.

**Line 5**: Queries MySQL: `SELECT * FROM users WHERE username = ?`. If no user is found, throws `UsernameNotFoundException`.

**Line 6-8**: Converts the `UserEntity` (our database model) to Spring Security's `UserDetails` (what Spring Security understands). The `User.builder()` creates a Spring Security `User` object with:
- Username and password (the BCrypt hash)
- Authorities — `ROLE_USER` or `ROLE_ADMIN` (from the enum)
- Disabled flag — disabled accounts can't authenticate

**Why two user classes?** `UserEntity` is our JPA entity (maps to database columns). `UserDetails` is Spring Security's interface (used for authentication). This separation keeps database concerns separate from security concerns.
