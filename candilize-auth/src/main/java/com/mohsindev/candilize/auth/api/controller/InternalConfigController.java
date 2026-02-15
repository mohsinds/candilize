package com.mohsindev.candilize.auth.api.controller;

import com.mohsindev.candilize.auth.api.dto.response.SchedulerConfigResponse;
import com.mohsindev.candilize.auth.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for market service. Secured by X-API-Key header.
 * Returns enabled pairs and intervals for the scheduler.
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalConfigController {

    private final ConfigService configService;

    @GetMapping("/scheduler-config")
    public ResponseEntity<SchedulerConfigResponse> getSchedulerConfig() {
        return ResponseEntity.ok(configService.getSchedulerConfig());
    }
}
