package com.mohsindev.candilize.service;

import com.mohsindev.candilize.infrastructure.enums.IndicatorName;

public interface Indicator <I, O>{
    O calculate(IndicatorName name, I input);
}
