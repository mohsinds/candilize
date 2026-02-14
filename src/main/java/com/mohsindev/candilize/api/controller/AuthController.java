package com.mohsindev.candilize.api.controller;

import com.mohsindev.candilize.api.dto.request.LoginRequest;
import com.mohsindev.candilize.api.dto.request.RegisterRequest;
import com.mohsindev.candilize.api.dto.response.AuthResponse;
import com.mohsindev.candilize.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller. All endpoints are public (no JWT required).
 * Handles user registration, login (returns JWT access + refresh tokens), and token refresh.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Registers a new user. Password is BCrypt-hashed before storage. */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** Authenticates user and returns access token + refresh token (JWT). */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /** Issues new access and refresh tokens using a valid refresh token. Body: { "refreshToken": "..." }. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }
}
