package com.mohsindev.candilize.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PairRequest(
        @NotBlank(message = "Symbol is required")
        @Size(max = 20)
        String symbol,

        @NotBlank(message = "Base asset is required")
        @Size(max = 10)
        String baseAsset,

        @NotBlank(message = "Quote asset is required")
        @Size(max = 10)
        String quoteAsset
) {}
