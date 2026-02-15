package com.mohsindev.candilize.market.infrastructure.market;

import com.mohsindev.candilize.market.api.exception.UnsupportedExchangeException;
import com.mohsindev.candilize.market.infrastructure.enums.ExchangeName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for obtaining the correct CandleDataProvider by exchange name. When app.testing-mode=true,
 * always returns TestCandleDataProvider (dummy data). Otherwise resolves by exchange code (e.g. binance, mexc)
 * and throws UnsupportedExchangeException if the exchange is not supported.
 */
@Slf4j
@Component
public class CandleDataProviderFactory {

    private final Map<String, CandleDataProvider> providersByExchange;
    private final boolean testingMode;
    private final CandleDataProvider testProvider;

    public CandleDataProviderFactory(
            List<CandleDataProvider> providers,
            @Value("${app.testing-mode:false}") boolean testingMode) {
        this.providersByExchange = providers.stream()
                .filter(p -> p.getExchangeName() != ExchangeName.TEST)
                .collect(Collectors.toUnmodifiableMap(
                        p -> p.getExchangeName().getCode().toLowerCase(),
                        Function.identity()));
        this.testingMode = testingMode;
        this.testProvider = providers.stream()
                .filter(p -> p.getExchangeName() == ExchangeName.TEST)
                .findFirst()
                .orElse(null);

        if (testingMode) {
            log.warn("⚠ TESTING MODE ENABLED - Using dummy price data");
        }
    }

    public CandleDataProvider getProvider(String exchangeName) {
        if (testingMode) {
            if (testProvider == null) {
                throw new IllegalStateException("Testing mode enabled but TestCandleDataProvider not available");
            }
            return testProvider;
        }

        String key = exchangeName != null ? exchangeName.toLowerCase() : "";
        CandleDataProvider provider = providersByExchange.get(key);
        if (provider == null) {
            throw new UnsupportedExchangeException("Unsupported exchange: " + exchangeName);
        }
        return provider;
    }
}
