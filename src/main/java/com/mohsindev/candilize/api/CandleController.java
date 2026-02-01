package com.mohsindev.candilize.api;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.enums.CandleInterval;
import com.mohsindev.candilize.infrastructure.enums.ExchangeName;
import com.mohsindev.candilize.service.CandleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/candles")
public class CandleController {

    private final CandleService candleService;

    public CandleController(CandleService candleService) {
        this.candleService = candleService;
    }

    /**
     * Returns OHLCV candles from the abstract CandleService (Strategy: selected exchange provider).
     * Query params: optional exchange (default from config). Path: pair, interval, limit.
     */
    @GetMapping("/{pair}/{interval}/{limit}")
    public ResponseEntity<List<Ohlcv>> getCandles(
            @PathVariable String pair,
            @PathVariable String interval,
            @PathVariable int limit,
            @RequestParam(required = false) String exchange
    ) {
        CandleInterval candleInterval = CandleInterval.parseCode(interval);
        List<Ohlcv> candles = exchange != null && !exchange.isBlank()
                ? candleService.getCandles(pair, candleInterval, limit, ExchangeName.parseCode(exchange))
                : candleService.getCandles(pair, candleInterval, limit);
        return ResponseEntity.ok(candles);
    }
}
