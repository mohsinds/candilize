package com.mohsindev.candilize.service.impl;

import com.mohsindev.candilize.configuration.ExchangeProperties;
import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.enums.ExchangeName;
import com.mohsindev.candilize.infrastructure.markets.CandleDataProvider;
import com.mohsindev.candilize.service.CandleService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Delegates candle fetching to the selected CandleDataProvider (Strategy pattern).
 * Depends on the abstraction CandleDataProvider, not concrete exchange clients.
 */
@Service
public class CandleServiceImpl implements CandleService {

    private final Map<ExchangeName, CandleDataProvider> providerByExchange;
    private final ExchangeProperties exchangeProperties;

    public CandleServiceImpl(List<CandleDataProvider> providers, ExchangeProperties exchangeProperties) {
        this.providerByExchange = providers.stream()
                .collect(Collectors.toUnmodifiableMap(CandleDataProvider::getExchangeName, Function.identity()));
        this.exchangeProperties = exchangeProperties;
    }

    @Override
    public int downloadMarketData(String pair, CandleInterval interval, Instant from, Instant to, boolean forceDownload) {
        return 0;
    }

    @Override
    public List<Ohlcv> getMarketData(String pair, CandleInterval interval, Instant from, Instant to) {
        return List.of();
    }

    @Override
    public List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit) {
        return getCandles(pair, interval, limit, exchangeProperties.defaultExchange());
    }

    @Override
    public List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit, ExchangeName exchange) {
        CandleDataProvider provider = providerByExchange.get(exchange);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        }
        return provider.getCandles(pair, interval, limit);
    }
}
