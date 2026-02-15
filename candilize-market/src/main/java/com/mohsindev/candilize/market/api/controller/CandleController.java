package com.mohsindev.candilize.market.api.controller;

import com.mohsindev.candilize.market.api.dto.response.CandleResponse;
import com.mohsindev.candilize.market.service.CandleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/candles")
@RequiredArgsConstructor
public class CandleController {

    private final CandleQueryService candleQueryService;

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

    @GetMapping("/{pair}")
    public ResponseEntity<List<String>> getAvailableIntervals(@PathVariable String pair) {
        List<String> intervals = candleQueryService.getAvailableIntervalsForPair(pair);
        return ResponseEntity.ok(intervals);
    }
}
