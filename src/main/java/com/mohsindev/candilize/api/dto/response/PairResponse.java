package com.mohsindev.candilize.api.dto.response;

public record PairResponse(
        Long id,
        String symbol,
        String baseAsset,
        String quoteAsset,
        Boolean enabled
) {}
