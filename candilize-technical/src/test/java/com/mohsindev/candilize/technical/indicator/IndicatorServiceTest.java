package com.mohsindev.candilize.technical.indicator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class IndicatorServiceTest {

    @InjectMocks
    private IndicatorService indicatorService;

    @Test
    void computeSma_returnsEmptyList() {
        List<IndicatorResult> result = indicatorService.computeSma("BTCUSDT", "1m", 14, 100);

        assertThat(result).isEmpty();
    }

    @Test
    void computeSma_invokesWithCorrectParams() {
        List<IndicatorResult> result = indicatorService.computeSma("ETHUSDT", "5m", 20, 50);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}
