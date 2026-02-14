package com.mohsindev.candilize.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/v1/auth/login. */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}
