package com.mohsindev.candilize.service;

import com.mohsindev.candilize.api.dto.response.CandleResponse;
import com.mohsindev.candilize.api.exception.EntityNotFoundException;
import com.mohsindev.candilize.infrastructure.persistence.document.CandleDataDocument;
import com.mohsindev.candilize.infrastructure.persistence.repository.CandleDataMongoRepository;
import com.mohsindev.candilize.infrastructure.persistence.repository.SupportedIntervalRepository;
import com.mohsindev.candilize.infrastructure.persistence.repository.SupportedPairRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for querying candle data from MongoDB. Validates that the requested pair and interval
 * are enabled in config (supported_pairs, supported_intervals in MySQL). Results are cacheable (Redis).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleQueryService {

    private final CandleDataMongoRepository candleDataMongoRepository;
    private final SupportedPairRepository pairRepository;
    private final SupportedIntervalRepository intervalRepository;

    /** Fetches candles with optional time range and exchange filter. Results are cached (cache-aside). */
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
            value = "candles",
            key = "#pair + ':' + #intervalCode + ':' + (#startTime != null ? #startTime : 0) + ':' + (#endTime != null ? #endTime : 9223372036854775807) + ':' + #limit + ':' + (#exchange != null && !#exchange.isBlank() ? #exchange : 'default')"
    )
    public List<CandleResponse> getCandles(
            String pair,
            String intervalCode,
            int limit,
            Long startTime,
            Long endTime,
            String exchange) {
        validatePair(pair);
        validateInterval(intervalCode);

        Long start = startTime != null ? startTime : 0L;
        Long end = endTime != null ? endTime : Long.MAX_VALUE;

        List<CandleDataDocument> docs;
        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "openTime"));

        if (exchange != null && !exchange.isBlank()) {
            docs = candleDataMongoRepository.findBySymbolAndIntervalCodeAndOpenTimeBetweenAndExchangeOrderByOpenTimeDesc(
                    pair.toUpperCase(), intervalCode, start, end, exchange.toLowerCase(), page);
        } else {
            docs = candleDataMongoRepository.findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeDesc(
                    pair.toUpperCase(), intervalCode, start, end, page);
        }

        return docs.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Returns distinct interval codes that have at least one candle stored for this pair. */
    @Transactional(readOnly = true)
    public List<String> getAvailableIntervalsForPair(String pair) {
        validatePair(pair);
        return candleDataMongoRepository.findDistinctIntervalCodesBySymbol(pair.toUpperCase());
    }

    /** Ensures the pair exists and is enabled; throws EntityNotFoundException otherwise. */
    private void validatePair(String pair) {
        if (pairRepository.findBySymbol(pair.toUpperCase())
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .isEmpty()) {
            throw new EntityNotFoundException("Pair not found or disabled: " + pair);
        }
    }

    /** Ensures the interval exists and is enabled. */
    private void validateInterval(String intervalCode) {
        if (intervalRepository.findByIntervalCode(intervalCode)
                .filter(i -> Boolean.TRUE.equals(i.getEnabled()))
                .isEmpty()) {
            throw new EntityNotFoundException("Interval not found or disabled: " + intervalCode);
        }
    }

    private CandleResponse toResponse(CandleDataDocument e) {
        return new CandleResponse(
                e.getSymbol(),
                e.getIntervalCode(),
                e.getOpenTime(),
                e.getOpenPrice(),
                e.getHighPrice(),
                e.getLowPrice(),
                e.getClosePrice(),
                e.getVolume(),
                e.getCloseTime(),
                e.getExchange()
        );
    }
}
