package com.mohsindev.candilize.technical.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for market scanning (e.g. find symbols meeting criteria).
 */
@Slf4j
@Service
public class ScannerService {

    public List<ScanResult> scan(String criteria, String interval) {
        log.info("Scanning for criteria={}, interval={}", criteria, interval);
        return List.of();
    }
}
