# Security — SecurityConfig, JwtAuthenticationFilter, AuthEntryPoint

## SecurityConfig

**File**: `candilize-market/.../security/SecurityConfig.java`

### What This File Does

Configures Spring Security for the market service. JWT validation via gRPC to `candilize-auth`. Download endpoints require admin role; candle/cache endpoints require any valid JWT.

### Full Source with Commentary

```java
@Configuration
@EnableWebSecurity                                             // 1
@EnableMethodSecurity                                          // 2
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter
        jwtAuthenticationFilter;
    private final AuthEntryPoint authEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)             // 3
            .cors(cors -> cors.configurationSource(
                corsConfigurationSource()))                    // 4
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))          // 5
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authEntryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**")
                    .permitAll()                               // 6
                .requestMatchers("/swagger-ui.html",
                    "/swagger-ui/**", "/v3/api-docs/**",
                    "/swagger-resources/**")
                    .permitAll()                               // 7
                .requestMatchers("/api/v1/candles/**")
                    .authenticated()                           // 8
                .requestMatchers("/api/v1/cache/**")
                    .authenticated()
                .requestMatchers("/api/v1/download/**")
                    .hasRole("ADMIN")                          // 9
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);   // 10

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() { // 11
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(
            Arrays.asList("GET", "POST", "PUT",
                "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

| # | What it does |
|---|---|
| 1 | `@EnableWebSecurity` — activates Spring Security's web filter chain. |
| 2 | `@EnableMethodSecurity` — enables `@PreAuthorize` on methods (used by `DownloadController`). |
| 3 | CSRF disabled — not needed for stateless JWT APIs. |
| 4 | CORS configuration — allows all origins (development setting). |
| 5 | `STATELESS` — no HTTP sessions. Each request carries its own JWT. |
| 6 | `/actuator/**` — health, metrics, gRPC info endpoints are public. |
| 7 | Swagger UI endpoints are public — allows browsing API docs without auth. |
| 8 | `/api/v1/candles/**` and `/api/v1/cache/**` — any authenticated user. |
| 9 | `/api/v1/download/**` — admin only. Combines with `@PreAuthorize` on `DownloadController`. |
| 10 | JWT filter runs before Spring's default auth filter. |
| 11 | CORS — allows all origins, methods, and headers. In production, restrict to specific origins. |

### Authorization Summary

| Path | Access |
|---|---|
| `/actuator/**` | Public |
| `/swagger-ui/**`, `/v3/api-docs/**` | Public |
| `/api/v1/candles/**` | Authenticated (any role) |
| `/api/v1/cache/**` | Authenticated (any role) |
| `/api/v1/download/**` | ROLE_ADMIN only |

---

## JwtAuthenticationFilter

**File**: `candilize-market/.../security/JwtAuthenticationFilter.java`

### What This File Does

Intercepts every HTTP request, extracts the JWT from `Authorization: Bearer <token>`, validates via gRPC to `candilize-auth`, and sets Spring Security context.

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
            if (StringUtils.hasText(jwt)) {
                AuthGrpcClient.ValidateResult result =
                    authGrpcClient.validateToken(jwt);         // 4
                if (result.valid()) {
                    List<SimpleGrantedAuthority> authorities =
                        result.roles().stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());     // 5
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                            result.username(), null,
                            authorities);                      // 6
                    SecurityContextHolder.getContext()
                        .setAuthentication(auth);              // 7
                }
            }
        } catch (Exception ex) {
            log.debug("Cannot set user authentication: {}",
                ex.getMessage());                              // 8
        }
        filterChain.doFilter(request, response);               // 9
    }

    private String extractJwtFromRequest(
            HttpServletRequest request) {
        String bearerToken =
            request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken)
                && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);                   // 10
        }
        return null;
    }
}
```

| # | What it does |
|---|---|
| 1 | `OncePerRequestFilter` — runs exactly once per request. |
| 2 | `AuthGrpcClient` — validates JWT via gRPC to auth:9090. |
| 3 | Extracts token from `Authorization: Bearer <token>` header. |
| 4 | gRPC call to auth service. Returns `ValidateResult` with username and roles. |
| 5 | Converts `["ROLE_USER"]` strings to `SimpleGrantedAuthority` objects. |
| 6 | Creates authentication token: principal=username, credentials=null, authorities=roles. |
| 7 | Sets authentication in `SecurityContextHolder`. Request is now authenticated. |
| 8 | On any failure (gRPC timeout, auth down), log and continue unauthenticated. |
| 9 | **Always call `filterChain.doFilter()`** — passing request to next filter. Forgetting this blocks all requests. |
| 10 | `substring(7)` — strips `"Bearer "` prefix (7 chars). |

---

## AuthEntryPoint

**File**: `candilize-market/.../security/AuthEntryPoint.java`

Returns JSON 401 response when an unauthenticated request hits a protected endpoint. Same pattern as the technical module's `AuthEntryPoint`.

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired JWT token",
  "path": "/api/v1/candles/BTCUSDT/1h"
}
```
