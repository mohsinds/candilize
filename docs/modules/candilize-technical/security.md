# Security — SecurityConfig, JwtAuthenticationFilter, AuthEntryPoint

## SecurityConfig

**File**: `candilize-technical/.../security/SecurityConfig.java`

### What This File Does

Configures Spring Security for the technical service. All API endpoints require JWT authentication. Tokens are validated via gRPC call to `candilize-auth`.

### Full Source with Commentary

```java
@Configuration
@EnableWebSecurity                                             // 1
@EnableMethodSecurity                                          // 2
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter
        jwtAuthenticationFilter;                               // 3
    private final AuthEntryPoint authEntryPoint;               // 4

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)             // 5
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))          // 6
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authEntryPoint))     // 7
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**")
                    .permitAll()                               // 8
                .requestMatchers("/api/v1/indicator/**")
                    .authenticated()                           // 9
                .requestMatchers("/api/v1/scanner/**")
                    .authenticated()
                .requestMatchers("/api/v1/strategy/**")
                    .authenticated()
                .requestMatchers("/api/v1/backtest/**")
                    .authenticated()
                .anyRequest().authenticated())                 // 10
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);   // 11

        return http.build();
    }
}
```

| # | What it does |
|---|---|
| 1 | `@EnableWebSecurity` — activates Spring Security's web security support. |
| 2 | `@EnableMethodSecurity` — allows `@PreAuthorize`, `@Secured` on individual methods. Not currently used in this module but available for future use. |
| 3 | `JwtAuthenticationFilter` — custom filter that validates JWTs via gRPC. |
| 4 | `AuthEntryPoint` — handles 401 responses when authentication fails. |
| 5 | CSRF disabled — not needed for stateless REST APIs with JWT auth. CSRF protects against form-based attacks using session cookies, which we don't use. |
| 6 | `STATELESS` — Spring Security won't create HTTP sessions. Each request must carry its own JWT token. |
| 7 | `authenticationEntryPoint(authEntryPoint)` — when a request fails authentication, `AuthEntryPoint.commence()` is called to return a JSON error. |
| 8 | `/actuator/**` — health, metrics, and gRPC info endpoints are public (no auth needed). |
| 9 | All four API endpoint groups require authentication (valid JWT). |
| 10 | Catch-all — any other endpoint also requires authentication. |
| 11 | Adds JWT filter **before** the default `UsernamePasswordAuthenticationFilter`. This ensures JWT validation runs first. |

---

## JwtAuthenticationFilter

**File**: `candilize-technical/.../security/JwtAuthenticationFilter.java`

### What This File Does

A servlet filter that intercepts every HTTP request, extracts the JWT from the `Authorization` header, validates it via gRPC call to `candilize-auth`, and sets the Spring Security context.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {                         // 1

    private final AuthGrpcClient authGrpcClient;              // 2

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
                throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);       // 3

            if (StringUtils.hasText(jwt)) {                    // 4
                AuthGrpcClient.ValidateResult result =
                    authGrpcClient.validateToken(jwt);         // 5
                if (result.valid()) {
                    List<SimpleGrantedAuthority> authorities =
                        result.roles().stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());     // 6
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                            result.username(),
                            null,
                            authorities);                      // 7
                    SecurityContextHolder.getContext()
                        .setAuthentication(auth);              // 8
                }
            }
        } catch (Exception ex) {
            log.debug("Cannot set user authentication: {}",
                ex.getMessage());                              // 9
        }

        filterChain.doFilter(request, response);               // 10
    }

    private String extractJwtFromRequest(
            HttpServletRequest request) {
        String bearerToken =
            request.getHeader("Authorization");                // 11
        if (StringUtils.hasText(bearerToken)
                && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);                   // 12
        }
        return null;
    }
}
```

| # | What it does |
|---|---|
| 1 | `OncePerRequestFilter` — Spring filter that guarantees the filter runs exactly once per request (even if the request is forwarded internally). |
| 2 | `AuthGrpcClient` — gRPC client to the auth service. Injected via constructor. |
| 3 | Extracts the JWT from the `Authorization: Bearer <token>` header. |
| 4 | Only attempt validation if a token is present. Public endpoints (like `/actuator/health`) won't have a token. |
| 5 | `authGrpcClient.validateToken(jwt)` — gRPC call to `candilize-auth:9090`. Returns a `ValidateResult` record. |
| 6 | Converts role strings (e.g., `"ROLE_USER"`, `"ROLE_ADMIN"`) to Spring Security `SimpleGrantedAuthority` objects. |
| 7 | Creates an authentication token. The three parameters are: principal (username), credentials (null — we don't need the password), and authorities (roles). |
| 8 | Sets the authentication in `SecurityContextHolder`. After this, Spring Security considers the request authenticated. `@PreAuthorize("hasRole('ADMIN')")` checks these authorities. |
| 9 | If anything fails (gRPC timeout, auth service down), log at DEBUG level and continue. The request proceeds unauthenticated — Spring Security will reject it with 401 if the endpoint requires auth. |
| 10 | **Always call `filterChain.doFilter()`** — this passes the request to the next filter in the chain. Forgetting this would block all requests. |
| 11 | Reads the `Authorization` header from the HTTP request. |
| 12 | `substring(7)` — strips the `"Bearer "` prefix (7 characters) to get the raw JWT. |

### Request Flow

```
Client request with JWT
  -> JwtAuthenticationFilter.doFilterInternal()
    -> extractJwtFromRequest() -> "eyJhbGci..."
    -> authGrpcClient.validateToken("eyJhbGci...")
      -> gRPC to auth:9090
      <- ValidateResult(valid=true, username="admin", roles=["ROLE_ADMIN"])
    -> SecurityContextHolder.setAuthentication(admin, [ROLE_ADMIN])
  -> filterChain.doFilter() -> controller method executes
```

---

## AuthEntryPoint

**File**: `candilize-technical/.../security/AuthEntryPoint.java`

### What This File Does

Handles authentication failures — when a request reaches a protected endpoint without a valid JWT. Returns a structured JSON error response.

```java
@Component
@RequiredArgsConstructor
public class AuthEntryPoint
        implements AuthenticationEntryPoint {                  // 1

    private final ObjectMapper objectMapper;                   // 2

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
                throws IOException {
        response.setContentType(
            MediaType.APPLICATION_JSON_VALUE);                 // 3
        response.setStatus(
            HttpServletResponse.SC_UNAUTHORIZED);              // 4
        objectMapper.writeValue(
            response.getOutputStream(),
            Map.of(
                "status", 401,
                "error", "Unauthorized",
                "message", "Invalid or expired JWT token",
                "path", request.getRequestURI()
            ));                                                // 5
    }
}
```

| # | What it does |
|---|---|
| 1 | `AuthenticationEntryPoint` — Spring Security interface. Called when an unauthenticated request hits a protected endpoint. |
| 2 | `ObjectMapper` — Jackson JSON serializer. Provided by `JacksonConfig`. |
| 3 | Sets `Content-Type: application/json`. |
| 4 | Sets HTTP status 401 Unauthorized. |
| 5 | Writes a JSON error response directly to the output stream. Uses `Map.of()` for a simple JSON object. |

### Example 401 Response

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired JWT token",
  "path": "/api/v1/indicator/BTCUSDT/1h/sma"
}
```
