package com.mohsindev.candilize.market.service;

import com.mohsindev.candilize.market.domain.KafkaPriceRequest;
import com.mohsindev.candilize.market.domain.Ohlcv;
import com.mohsindev.candilize.market.enums.CandleInterval;
import com.mohsindev.candilize.market.infrastructure.market.CandleDataProvider;
import com.mohsindev.candilize.market.infrastructure.market.CandleDataProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleDownloadService {

    private final CandleDataProviderFactory providerFactory;
    private final CandleDataPersistenceService persistenceService;

    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    public int downloadAndPersist(KafkaPriceRequest.PriceObject po) {
        String pair = po.getPair();
        String interval = po.getInterval();
        int limit = po.getLimit();
        String exchange = po.getExchange() != null && !po.getExchange().isBlank()
                ? po.getExchange() : "binance";

        CandleDataProvider provider = providerFactory.getProvider(exchange);
        CandleInterval candleInterval = CandleInterval.parseCode(interval);
        List<Ohlcv> candles = provider.getCandles(pair, candleInterval, limit);

        return persistenceService.persistCandles(candles, pair, interval, exchange);
    }
}
