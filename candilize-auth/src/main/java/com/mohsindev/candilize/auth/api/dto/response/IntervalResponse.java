package com.mohsindev.candilize.auth.api.dto.response;

public record IntervalResponse(
        Long id,
        String intervalCode,
        String description,
        Boolean enabled
) {}
