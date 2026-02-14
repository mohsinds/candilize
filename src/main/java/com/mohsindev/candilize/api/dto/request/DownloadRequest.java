package com.mohsindev.candilize.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record DownloadRequest(
        @NotBlank(message = "Pair is required")
        String pair,

        @NotBlank(message = "Interval is required")
        String interval,

        @NotBlank(message = "Exchange is required")
        String exchange,

        @PositiveOrZero
        Integer limit,

        Long startTime,
        Long endTime
) {}
