package com.mohsindev.candilize.auth.api.dto.response;

public record PairResponse(
        Long id,
        String symbol,
        String baseAsset,
        String quoteAsset,
        Boolean enabled
) {}
