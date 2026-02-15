package com.mohsindev.candilize.market.configuration;

import com.mohsindev.candilize.market.infrastructure.enums.ExchangeName;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ExchangeNameConverter implements Converter<String, ExchangeName> {

    @Override
    public ExchangeName convert(String source) {
        if (source == null || source.isBlank()) {
            return ExchangeName.BINANCE;
        }
        try {
            return ExchangeName.parseCode(source.trim());
        } catch (Exception e) {
            return ExchangeName.BINANCE;
        }
    }
}
