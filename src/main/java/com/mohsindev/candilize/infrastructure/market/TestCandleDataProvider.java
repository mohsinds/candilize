package com.mohsindev.candilize.infrastructure.market;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.enums.ExchangeName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.testing-mode", havingValue = "true")
public class TestCandleDataProvider implements CandleDataProvider {

    private static final Map<String, BigDecimal> BASE_PRICES = Map.of(
            "BTCUSDT", new BigDecimal("42000"),
            "ETHUSDT", new BigDecimal("2200"),
            "SOLUSDT", new BigDecimal("100"),
            "XRPUSDT", new BigDecimal("0.55"),
            "ADAUSDT", new BigDecimal("0.45")
    );

    private static final BigDecimal DEFAULT_BASE = new BigDecimal("1");
    private static final BigDecimal VARIANCE_PCT = new BigDecimal("0.02");
    private static final BigDecimal RANDOM_PCT = new BigDecimal("0.005");

    @Override
    public ExchangeName getExchangeName() {
        return ExchangeName.TEST;
    }

    @Override
    public List<Ohlcv> getCandles(String pair, CandleInterval interval, int limit) {
        BigDecimal basePrice = BASE_PRICES.getOrDefault(pair.toUpperCase(), DEFAULT_BASE);
        List<Ohlcv> candles = new ArrayList<>(limit);

        long nowSeconds = Instant.now().getEpochSecond();
        long intervalSeconds = interval.getSeconds();
        long alignedNow = (nowSeconds / intervalSeconds) * intervalSeconds;
        long closeTimeMs = alignedNow * 1000L;

        BigDecimal prevClose = basePrice;

        for (int i = limit - 1; i >= 0; i--) {
            long openTime = (alignedNow - (i + 1) * intervalSeconds) * 1000L;
            long candleCloseTime = (alignedNow - i * intervalSeconds) * 1000L;

            BigDecimal open = prevClose;
            BigDecimal randomMove = randomPct(RANDOM_PCT);
            BigDecimal close = open.multiply(BigDecimal.ONE.add(randomMove)).setScale(8, RoundingMode.HALF_UP);

            BigDecimal highPct = randomPct(new BigDecimal("0.003"));
            BigDecimal lowPct = randomPct(new BigDecimal("0.003"));
            BigDecimal high = open.max(close).multiply(BigDecimal.ONE.add(highPct)).setScale(8, RoundingMode.HALF_UP);
            BigDecimal low = open.min(close).multiply(BigDecimal.ONE.subtract(lowPct)).setScale(8, RoundingMode.HALF_UP);

            if (high.compareTo(low) <= 0) {
                high = low.add(new BigDecimal("0.00000001"));
            }

            BigDecimal volume = BigDecimal.valueOf(100 + Math.random() * 9900).setScale(8, RoundingMode.HALF_UP);

            candles.add(Ohlcv.builder()
                    .interval(interval)
                    .timestamp(Instant.ofEpochMilli(openTime))
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .build());

            prevClose = close;
        }

        return candles;
    }

    private BigDecimal randomPct(BigDecimal maxPct) {
        double d = (Math.random() - 0.5) * 2 * maxPct.doubleValue();
        return BigDecimal.valueOf(d).setScale(8, RoundingMode.HALF_UP);
    }
}
