package com.mohsindev.candilize.api;

import com.mohsindev.candilize.api.dto.response.CandleResponse;
import com.mohsindev.candilize.service.CandleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for candle (OHLCV) data. All endpoints require JWT authentication.
 * Data is read from the database (candle_data table) with optional Redis caching.
 * Pair and interval must exist in supported_pairs and supported_intervals and be enabled.
 */
@RestController
@RequestMapping("/api/v1/candles")
@RequiredArgsConstructor
public class CandleController {

    private final CandleQueryService candleQueryService;

    /**
     * Returns paginated candles for the given pair and interval.
     * Optional filters: startTime, endTime (epoch ms), exchange. Results ordered by openTime descending.
     */
    @GetMapping("/{pair}/{interval}")
    public ResponseEntity<List<CandleResponse>> getCandles(
            @PathVariable String pair,
            @PathVariable String interval,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) String exchange) {
        List<CandleResponse> candles = candleQueryService.getCandles(
                pair, interval, limit, startTime, endTime, exchange);
        return ResponseEntity.ok(candles);
    }

    /** Returns the list of interval codes that have stored candle data for this pair. */
    @GetMapping("/{pair}")
    public ResponseEntity<List<String>> getAvailableIntervals(@PathVariable String pair) {
        List<String> intervals = candleQueryService.getAvailableIntervalsForPair(pair);
        return ResponseEntity.ok(intervals);
    }
}
