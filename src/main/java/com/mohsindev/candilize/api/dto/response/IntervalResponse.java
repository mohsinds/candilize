package com.mohsindev.candilize.api.dto.response;

public record IntervalResponse(
        Long id,
        String intervalCode,
        String description,
        Boolean enabled
) {}
