package com.mohsindev.candilize.auth.api.controller;

import com.mohsindev.candilize.auth.api.dto.request.PairRequest;
import com.mohsindev.candilize.auth.api.dto.response.IntervalResponse;
import com.mohsindev.candilize.auth.api.dto.response.PairResponse;
import com.mohsindev.candilize.auth.service.ConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/pairs")
    public ResponseEntity<List<PairResponse>> listPairs() {
        return ResponseEntity.ok(configService.getAllPairs());
    }

    @PostMapping("/pairs")
    public ResponseEntity<PairResponse> addPair(@Valid @RequestBody PairRequest request) {
        PairResponse response = configService.addPair(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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

    @GetMapping("/intervals")
    public ResponseEntity<List<IntervalResponse>> listIntervals() {
        return ResponseEntity.ok(configService.getAllIntervals());
    }

    @PutMapping("/intervals/{id}")
    public ResponseEntity<IntervalResponse> updateInterval(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body != null ? body.get("enabled") : null;
        IntervalResponse response = configService.updateInterval(id, enabled);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/exchanges")
    public ResponseEntity<List<String>> listExchanges() {
        return ResponseEntity.ok(configService.getAvailableExchanges());
    }
}
