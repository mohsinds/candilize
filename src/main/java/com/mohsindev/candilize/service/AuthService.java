package com.mohsindev.candilize.service;

import com.mohsindev.candilize.api.dto.request.LoginRequest;
import com.mohsindev.candilize.api.dto.request.RegisterRequest;
import com.mohsindev.candilize.api.dto.response.AuthResponse;
import com.mohsindev.candilize.infrastructure.persistence.entity.UserEntity;
import com.mohsindev.candilize.infrastructure.persistence.repository.UserRepository;
import com.mohsindev.candilize.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration, login, and JWT refresh. Uses BCrypt for password hashing and
 * JwtTokenProvider for issuing access/refresh tokens. Delegates authentication to Spring's
 * AuthenticationManager for login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpirationMs;

    /** Persists a new user with encoded password. Throws if username or email already exists. */
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already exists: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists: " + request.email());
        }

        UserEntity user = UserEntity.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(UserEntity.Role.ROLE_USER)
                .enabled(true)
                .build();
        userRepository.save(user);
        log.info("Registered user: {}", request.username());
    }

    /** Authenticates credentials and returns new access + refresh JWTs. */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        return AuthResponse.of(
                accessToken,
                refreshToken,
                accessTokenExpirationMs / 1000
        );
    }

    /** Validates refresh token and issues new access + refresh tokens. */
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        return AuthResponse.of(
                newAccessToken,
                newRefreshToken,
                accessTokenExpirationMs / 1000
        );
    }
}
