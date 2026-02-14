package com.mohsindev.candilize.api.controller;

import com.mohsindev.candilize.api.dto.request.PairRequest;
import com.mohsindev.candilize.api.dto.response.IntervalResponse;
import com.mohsindev.candilize.api.dto.response.PairResponse;
import com.mohsindev.candilize.service.ConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only controller for managing supported pairs and intervals (config data).
 * Used by the scheduler to decide which pair/interval combinations to download.
 * Responses may be cached (supportedPairs, supportedIntervals) with TTL from application properties.
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ConfigController {

    private final ConfigService configService;

    /** Lists all supported pairs (enabled and disabled). */
    @GetMapping("/pairs")
    public ResponseEntity<List<PairResponse>> listPairs() {
        return ResponseEntity.ok(configService.getAllPairs());
    }

    /** Adds a new trading pair. Symbol must be unique. */
    @PostMapping("/pairs")
    public ResponseEntity<PairResponse> addPair(@Valid @RequestBody PairRequest request) {
        PairResponse response = configService.addPair(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Updates a pair (e.g. enable/disable). Body: { "enabled": true/false }. */
    @PutMapping("/pairs/{id}")
    public ResponseEntity<PairResponse> updatePair(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body != null ? body.get("enabled") : null;
        PairResponse response = configService.updatePair(id, enabled);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/pairs/{id}")
    public ResponseEntity<Void> deletePair(@PathVariable Long id) {
        configService.deletePair(id);
        return ResponseEntity.noContent().build();
    }

    /** Lists all supported intervals. */
    @GetMapping("/intervals")
    public ResponseEntity<List<IntervalResponse>> listIntervals() {
        return ResponseEntity.ok(configService.getAllIntervals());
    }

    /** Enable/disable an interval. Body: { "enabled": true/false }. */
    @PutMapping("/intervals/{id}")
    public ResponseEntity<IntervalResponse> updateInterval(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body != null ? body.get("enabled") : null;
        IntervalResponse response = configService.updateInterval(id, enabled);
        return ResponseEntity.ok(response);
    }

    /** Returns available exchange codes (e.g. mexc, binance). */
    @GetMapping("/exchanges")
    public ResponseEntity<List<String>> listExchanges() {
        return ResponseEntity.ok(configService.getAvailableExchanges());
    }
}
