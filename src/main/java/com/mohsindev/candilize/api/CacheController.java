package com.mohsindev.candilize.api;

import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.service.CandleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/cache")
public class CacheController {

    private final CandleService candleService;

    public CacheController(CandleService candleService) {
        this.candleService = candleService;
    }

    @GetMapping("/refresh/{pair}/{interval}/{limit}")
    public ResponseEntity<?> refresh(
            @PathVariable String pair,
            @PathVariable String interval,
            @PathVariable int limit
    ) {
        /**
         *   - Manually triggers MEXC API fetch
         *   - Stores data in MongoDB candles_1h collection
         *   - Returns: Success/failure status
         */
        CandleInterval candleInterval = CandleInterval.parseCode(interval);
        this.candleService.downloadMarketData(pair, candleInterval, Instant.parse("2026-01-28T05:30:00Z"),Instant.parse("2026-01-31T05:30:00Z"), true);
        return null;
    }
}
