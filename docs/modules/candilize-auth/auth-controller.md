# AuthController — Registration, Login & Token Refresh

**File**: `candilize-auth/src/main/java/com/mohsindev/candilize/auth/api/controller/AuthController.java`

## What This File Does

This is the main authentication REST controller. It exposes three HTTP endpoints for user registration, login, and token refresh. All three are **publicly accessible** (no JWT required).

---

## Full Source with Line-by-Line Commentary

```java
package com.mohsindev.candilize.auth.api.controller;                        // 1

import com.mohsindev.candilize.auth.api.dto.request.LoginRequest;            // 2
import com.mohsindev.candilize.auth.api.dto.request.RegisterRequest;
import com.mohsindev.candilize.auth.api.dto.response.AuthResponse;
import com.mohsindev.candilize.auth.service.AuthService;
import jakarta.validation.Valid;                                              // 3
import lombok.RequiredArgsConstructor;                                        // 4
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;                             // 5

import java.util.Map;

@RestController                                                               // 6
@RequestMapping("/api/v1/auth")                                               // 7
@RequiredArgsConstructor                                                      // 8
public class AuthController {

    private final AuthService authService;                                    // 9

    @PostMapping("/register")                                                 // 10
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {  // 11
        authService.register(request);                                        // 12
        return ResponseEntity.status(HttpStatus.CREATED).build();             // 13
    }

    @PostMapping("/login")                                                    // 14
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);                   // 15
        return ResponseEntity.ok(response);                                   // 16
    }

    @PostMapping("/refresh")                                                  // 17
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");                       // 18
        if (refreshToken == null || refreshToken.isBlank()) {                 // 19
            return ResponseEntity.badRequest().build();
        }
        AuthResponse response = authService.refreshToken(refreshToken);       // 20
        return ResponseEntity.ok(response);
    }
}
```

---

## Annotation & Line Explanations

### Line 1: `package`
Every Java class belongs to a package. The package name matches the directory structure: `com/mohsindev/candilize/auth/api/controller/`. This is a naming convention that prevents class name collisions between projects.

### Lines 2-5: Imports
These import the classes used in this file. Java requires explicit imports (unlike Python or JavaScript where you can reference anything in scope).

### Line 3: `jakarta.validation.Valid`
`jakarta` is the namespace for Jakarta EE (formerly Java EE). The `@Valid` annotation triggers bean validation — it tells Spring to check the `@NotBlank`, `@Email`, `@Size` annotations on the request DTO fields before the method body runs. If validation fails, Spring throws `MethodArgumentNotValidException`, which is caught by `GlobalExceptionHandler`.

### Line 4: `lombok.RequiredArgsConstructor`
Lombok is a compile-time code generator. `@RequiredArgsConstructor` generates a constructor that takes all `final` fields as parameters. So the compiler generates:
```java
public AuthController(AuthService authService) {
    this.authService = authService;
}
```
This is how **dependency injection (DI)** works — Spring sees this constructor, looks for a bean of type `AuthService` in its container, and passes it in automatically.

### Line 6: `@RestController`
This is a combination of two annotations:
- `@Controller` — marks this class as a Spring MVC controller (handles HTTP requests)
- `@ResponseBody` — tells Spring to serialize return values to JSON automatically (instead of looking for an HTML template)

When Spring Boot starts, it scans for classes with `@Controller` or `@RestController` and registers them as request handlers.

### Line 7: `@RequestMapping("/api/v1/auth")`
Sets the **base URL path** for all endpoints in this controller. Every `@PostMapping` or `@GetMapping` in this class is relative to this path. So `@PostMapping("/register")` becomes `POST /api/v1/auth/register`.

### Line 8: `@RequiredArgsConstructor`
See line 4 explanation. Generates the constructor for DI.

### Line 9: `private final AuthService authService`
This field is injected by Spring's DI container. The `final` keyword means:
1. It must be set in the constructor (Lombok generates this)
2. It can never be reassigned after construction
3. It tells `@RequiredArgsConstructor` to include it in the generated constructor

**How DI works here**: When Spring creates `AuthController`, it needs an `AuthService`. It finds the `AuthService` bean (a class annotated with `@Service`), creates it (injecting its own dependencies), and passes it to this controller's constructor. This is called **constructor injection** — the recommended way to inject dependencies in Spring.

### Line 10: `@PostMapping("/register")`
Maps HTTP `POST /api/v1/auth/register` to this method. Spring dispatches incoming requests to the matching method based on the HTTP method (GET/POST/PUT/DELETE) and URL path.

### Line 11: Method signature
```java
public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request)
```

| Part | Meaning |
|---|---|
| `ResponseEntity<Void>` | Return type. `ResponseEntity` wraps the response body + HTTP status + headers. `Void` means no body. |
| `@Valid` | Triggers validation on `RegisterRequest`. If any field fails validation (e.g., blank username), Spring throws an exception before this method runs. |
| `@RequestBody` | Tells Spring to deserialize the HTTP request body (JSON) into a `RegisterRequest` object. Spring uses Jackson (JSON library) to do this. |
| `RegisterRequest request` | The deserialized request object. This is a Java record (immutable data class). |

### Line 12: `authService.register(request)`
Delegates to the service layer. The controller doesn't contain business logic — it only handles HTTP concerns (parsing request, returning response). The service handles:
1. Check if username/email already exists
2. Hash the password with BCrypt
3. Save the user to MySQL

### Line 13: `ResponseEntity.status(HttpStatus.CREATED).build()`
Returns HTTP 201 Created with no body. The `.build()` finalizes the response. `ResponseEntity` provides a fluent builder API for constructing HTTP responses.

### Line 14-16: Login endpoint
Same pattern as register, but returns an `AuthResponse` with JWT tokens.

### Line 17-20: Refresh endpoint
Takes a raw `Map<String, String>` instead of a typed DTO. This is a simpler approach for a single-field request. The map is deserialized from JSON like `{"refreshToken": "eyJ..."}`. If the refresh token is missing or blank, returns HTTP 400 Bad Request.

---

## Request/Response DTOs

### RegisterRequest
```java
public record RegisterRequest(
    @NotBlank(message = "Username is required")     // Validation: cannot be null or empty
    @Size(min = 2, max = 50)                        // Must be 2-50 characters
    String username,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")        // Must match email pattern
    @Size(max = 100)
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
) {}
```

A **record** is a Java 16+ feature — an immutable data class. The compiler generates:
- A constructor `RegisterRequest(String username, String email, String password)`
- Accessor methods `username()`, `email()`, `password()` (NOT `getUsername()` — records use the field name directly)
- `equals()`, `hashCode()`, and `toString()`

Records are perfect for DTOs (Data Transfer Objects) because they're concise, immutable, and automatically get all the boilerplate methods.

### LoginRequest
```java
public record LoginRequest(
    @NotBlank(message = "Username is required") String username,
    @NotBlank(message = "Password is required") String password
) {}
```

### AuthResponse
```java
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,       // Always "Bearer"
    long expiresIn          // Seconds until access token expires
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
```

The `of(...)` is a **static factory method** — a convenience method that fills in the `tokenType` automatically.

---

## How a Login Request Flows Through the System

```
1. Client sends POST /api/v1/auth/login with {"username":"admin","password":"admin123"}
2. Spring's DispatcherServlet receives the request
3. Security filter chain runs:
   a. InternalApiKeyFilter skips (not /internal/ path)
   b. JwtAuthenticationFilter skips (no Authorization header)
   c. SecurityConfig allows /api/v1/auth/** without authentication
4. Spring deserializes JSON body into LoginRequest
5. @Valid triggers validation — both fields are @NotBlank, both present → OK
6. AuthController.login() is called
7. AuthService.login() is called:
   a. AuthenticationManager.authenticate() checks username+password against DB
   b. UserDetailsService.loadUserByUsername() loads user from MySQL
   c. JwtTokenProvider.generateAccessToken() creates a JWT
   d. JwtTokenProvider.generateRefreshToken() creates a refresh JWT
   e. Returns AuthResponse with both tokens
8. Controller returns ResponseEntity.ok(response) → HTTP 200
9. Spring serializes AuthResponse to JSON using Jackson
10. Client receives:
    {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
      "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
      "tokenType": "Bearer",
      "expiresIn": 900
    }
```

---

## Testing with cURL

```bash
# Register a new user
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Refresh token (use the refreshToken from login response)
curl -X POST http://localhost:8081/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJhbGciOiJIUzI1NiJ9..."}'
```
