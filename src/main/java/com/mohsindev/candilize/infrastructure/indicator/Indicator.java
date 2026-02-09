package com.mohsindev.candilize.infrastructure.indicator;

import com.mohsindev.candilize.infrastructure.enums.IndicatorName;

public interface Indicator <I, O, P>{
    O calculate(I input, P parameter);
}
