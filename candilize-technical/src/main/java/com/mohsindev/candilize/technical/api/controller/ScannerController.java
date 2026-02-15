package com.mohsindev.candilize.technical.api.controller;

import com.mohsindev.candilize.technical.scanner.ScanResult;
import com.mohsindev.candilize.technical.scanner.ScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scanner")
@RequiredArgsConstructor
public class ScannerController {

    private final ScannerService scannerService;

    @GetMapping
    public ResponseEntity<List<ScanResult>> scan(
            @RequestParam(defaultValue = "sma_crossover") String criteria,
            @RequestParam(defaultValue = "1h") String interval) {
        List<ScanResult> results = scannerService.scan(criteria, interval);
        return ResponseEntity.ok(results);
    }
}
