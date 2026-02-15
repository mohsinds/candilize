package com.mohsindev.candilize.market.service;

import com.mohsindev.candilize.market.api.dto.response.CandleResponse;
import com.mohsindev.candilize.market.infrastructure.persistence.document.CandleDataDocument;
import com.mohsindev.candilize.market.infrastructure.persistence.repository.CandleDataMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Fetches candle data from MongoDB with optional Redis caching.
 * @Cacheable("candles") caches results by (pair, interval, startTime, endTime, limit, exchange).
 * Validates pair/interval via ConfigValidationService (which fetches scheduler config from auth).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleQueryService {

    private final CandleDataMongoRepository candleDataMongoRepository;
    private final ConfigValidationService configValidationService;

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
        configValidationService.validatePair(pair);
        configValidationService.validateInterval(intervalCode);

        Long start = startTime != null ? startTime : 0L;
        Long end = endTime != null ? endTime : Long.MAX_VALUE;

        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "openTime"));
        List<CandleDataDocument> docs;

        if (exchange != null && !exchange.isBlank()) {
            docs = candleDataMongoRepository.findBySymbolAndIntervalCodeAndOpenTimeBetweenAndExchangeOrderByOpenTimeDesc(
                    pair.toUpperCase(), intervalCode, start, end, exchange.toLowerCase(), page);
        } else {
            docs = candleDataMongoRepository.findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeDesc(
                    pair.toUpperCase(), intervalCode, start, end, page);
        }

        return docs.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<String> getAvailableIntervalsForPair(String pair) {
        configValidationService.validatePair(pair);
        return candleDataMongoRepository.findDistinctIntervalCodesBySymbol(pair.toUpperCase());
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
