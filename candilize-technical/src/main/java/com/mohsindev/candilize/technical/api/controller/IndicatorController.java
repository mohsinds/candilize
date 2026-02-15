package com.mohsindev.candilize.technical.api.controller;

import com.mohsindev.candilize.technical.indicator.IndicatorResult;
import com.mohsindev.candilize.technical.indicator.IndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/indicator")
@RequiredArgsConstructor
public class IndicatorController {

    private final IndicatorService indicatorService;

    @GetMapping("/{pair}/{interval}/sma")
    public ResponseEntity<List<IndicatorResult>> getSma(
            @PathVariable String pair,
            @PathVariable String interval,
            @RequestParam(defaultValue = "20") int period,
            @RequestParam(defaultValue = "100") int limit) {
        List<IndicatorResult> results = indicatorService.computeSma(pair, interval, period, limit);
        return ResponseEntity.ok(results);
    }
}
