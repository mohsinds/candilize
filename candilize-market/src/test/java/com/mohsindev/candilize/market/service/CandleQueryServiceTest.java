package com.mohsindev.candilize.market.service;

import com.mohsindev.candilize.market.api.dto.response.CandleResponse;
import com.mohsindev.candilize.market.infrastructure.persistence.document.CandleDataDocument;
import com.mohsindev.candilize.market.infrastructure.persistence.repository.CandleDataMongoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandleQueryServiceTest {

    @Mock
    private CandleDataMongoRepository candleDataMongoRepository;
    @Mock
    private ConfigValidationService configValidationService;

    @InjectMocks
    private CandleQueryService candleQueryService;

    @Test
    void getCandles_returnsMappedResponses() {
        CandleDataDocument doc = CandleDataDocument.builder()
                .symbol("BTCUSDT")
                .intervalCode("1m")
                .openTime(1_700_000_000_000L)
                .openPrice(BigDecimal.valueOf(40000))
                .highPrice(BigDecimal.valueOf(40100))
                .lowPrice(BigDecimal.valueOf(39900))
                .closePrice(BigDecimal.valueOf(40050))
                .volume(BigDecimal.valueOf(100))
                .closeTime(1_700_000_059_999L)
                .exchange("binance")
                .build();
        when(candleDataMongoRepository.findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeDesc(
                eq("BTCUSDT"), eq("1m"), eq(0L), eq(Long.MAX_VALUE), any(PageRequest.class)))
                .thenReturn(List.of(doc));

        List<CandleResponse> result = candleQueryService.getCandles("BTCUSDT", "1m", 100, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("BTCUSDT");
        assertThat(result.get(0).intervalCode()).isEqualTo("1m");
        assertThat(result.get(0).openPrice()).isEqualByComparingTo(BigDecimal.valueOf(40000));
        assertThat(result.get(0).exchange()).isEqualTo("binance");
        verify(configValidationService).validatePair("BTCUSDT");
        verify(configValidationService).validateInterval("1m");
    }

    @Test
    void getAvailableIntervalsForPair_returnsIntervalsFromRepository() {
        when(candleDataMongoRepository.findDistinctIntervalCodesBySymbol("BTCUSDT"))
                .thenReturn(List.of("1m", "5m", "15m"));

        List<String> result = candleQueryService.getAvailableIntervalsForPair("BTCUSDT");

        assertThat(result).containsExactly("1m", "5m", "15m");
        verify(configValidationService).validatePair("BTCUSDT");
    }
}
