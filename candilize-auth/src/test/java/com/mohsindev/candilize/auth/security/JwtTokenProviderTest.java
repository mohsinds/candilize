package com.mohsindev.candilize.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String BASE64_SECRET = "dGhpcyBpcyBhIHNlY3JldCBrZXkgZm9yIGRldmVsb3BtZW50IG9ubHk=";
    private JwtTokenProvider jwtTokenProvider;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(BASE64_SECRET, 900_000L, 604_800_000L);
        userDetails = new User("testuser", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void generateAccessToken_returnsNonEmptyToken() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalseForInvalidToken() {
        assertThat(jwtTokenProvider.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void getUsernameFromToken_returnsSubject() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);

        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("testuser");
    }
}
