package com.mohsindev.candilize.service;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.persistence.document.CandleDataDocument;
import com.mohsindev.candilize.infrastructure.persistence.repository.CandleDataMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Persists candle (OHLCV) data to MongoDB. Uses exists-check before insert to avoid duplicates. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleDataPersistenceService {

    private final CandleDataMongoRepository candleDataMongoRepository;

    @org.springframework.cache.annotation.CacheEvict(value = "candles", allEntries = true)
    public int persistCandles(List<Ohlcv> candles, String symbol, String intervalCode, String exchange) {
        int saved = 0;
        CandleInterval interval = candles.isEmpty() ? CandleInterval.parseCode(intervalCode) : candles.get(0).getInterval();
        long intervalMs = interval.getSeconds() * 1000L;

        for (Ohlcv o : candles) {
            long openTimeMs = o.getTimestamp().toEpochMilli();
            long closeTimeMs = openTimeMs + intervalMs;

            if (candleDataMongoRepository.existsBySymbolAndIntervalCodeAndOpenTimeAndExchange(
                    symbol, intervalCode, openTimeMs, exchange)) {
                continue;
            }

            CandleDataDocument doc = CandleDataDocument.builder()
                    .symbol(symbol)
                    .intervalCode(intervalCode)
                    .openTime(openTimeMs)
                    .openPrice(o.getOpen())
                    .highPrice(o.getHigh())
                    .lowPrice(o.getLow())
                    .closePrice(o.getClose())
                    .volume(o.getVolume())
                    .closeTime(closeTimeMs)
                    .exchange(exchange)
                    .build();
            candleDataMongoRepository.save(doc);
            saved++;
        }
        return saved;
    }
}
