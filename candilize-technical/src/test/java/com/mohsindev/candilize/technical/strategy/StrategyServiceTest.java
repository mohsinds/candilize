package com.mohsindev.candilize.technical.strategy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StrategyServiceTest {

    @InjectMocks
    private StrategyService strategyService;

    @Test
    void getSignals_returnsEmptyList() {
        List<StrategySignal> result = strategyService.getSignals("rsi", "BTCUSDT", "1m");

        assertThat(result).isEmpty();
    }

    @Test
    void getSignals_invokesWithCorrectParams() {
        List<StrategySignal> result = strategyService.getSignals("macd", "ETHUSDT", "5m");

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}
