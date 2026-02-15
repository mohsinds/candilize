package com.mohsindev.candilize.market.infrastructure.persistence.repository;

import java.util.List;

/** Custom MongoDB operations for candle data (e.g. distinct interval codes by symbol). */
public interface CandleDataMongoRepositoryCustom {

    List<String> findDistinctIntervalCodesBySymbol(String symbol);
}
