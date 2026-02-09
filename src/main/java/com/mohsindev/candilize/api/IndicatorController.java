package com.mohsindev.candilize.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/indicators/")
public class IndicatorController {

    @GetMapping("sma")
    public String sma(@RequestParam(required = true) String pair){
        return "asd";
    }
}
