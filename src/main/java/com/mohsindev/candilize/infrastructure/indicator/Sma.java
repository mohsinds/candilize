package com.mohsindev.candilize.infrastructure.indicator;

import com.mohsindev.candilize.domain.Ohlcv;
import com.mohsindev.candilize.infrastructure.enums.IndicatorName;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class Sma implements Indicator<Ohlcv[], double[], Integer> {

    @Override
    public double[] calculate(Ohlcv[] input, Integer period) {

        double[] sma = new double[input.length - period + 1];

        return sma;
//        List<Double> sma = new ArrayList<>();
//
//        for (int i = 0; i <= input.length - period; i++) {
//            sma.add(
//                    Arrays.stream(Arrays.copyOfRange(input,i,Math.min(i + period,input.length)))
//                    .map(Ohlcv::getClose)
//                    .filter(Objects::nonNull)
//                    .reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue() / period
//            );
//        }
//
//        return sma;
    }
}
