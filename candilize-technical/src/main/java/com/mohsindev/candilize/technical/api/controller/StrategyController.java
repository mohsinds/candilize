package com.mohsindev.candilize.technical.api.controller;

import com.mohsindev.candilize.technical.strategy.StrategyService;
import com.mohsindev.candilize.technical.strategy.StrategySignal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategyService;

    @GetMapping("/{strategy}/{pair}/{interval}")
    public ResponseEntity<List<StrategySignal>> getSignals(
            @PathVariable String strategy,
            @PathVariable String pair,
            @PathVariable String interval) {
        List<StrategySignal> signals = strategyService.getSignals(strategy, pair, interval);
        return ResponseEntity.ok(signals);
    }
}
