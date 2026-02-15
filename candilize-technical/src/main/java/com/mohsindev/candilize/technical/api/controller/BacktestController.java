package com.mohsindev.candilize.technical.api.controller;

import com.mohsindev.candilize.technical.backtest.BacktestRequest;
import com.mohsindev.candilize.technical.backtest.BacktestResult;
import com.mohsindev.candilize.technical.backtest.BacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    @PostMapping
    public ResponseEntity<BacktestResult> runBacktest(@RequestBody BacktestRequest request) {
        BacktestResult result = backtestService.run(request);
        return ResponseEntity.ok(result);
    }
}
