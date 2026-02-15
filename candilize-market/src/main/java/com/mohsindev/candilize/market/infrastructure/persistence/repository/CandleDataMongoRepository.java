package com.mohsindev.candilize.market.infrastructure.persistence.repository;

import com.mohsindev.candilize.market.infrastructure.persistence.document.CandleDataDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * MongoDB repository for candle data (collection: candle_data).
 * MySQL is used for users, supported_pairs, supported_intervals only.
 */
public interface CandleDataMongoRepository extends MongoRepository<CandleDataDocument, String>, CandleDataMongoRepositoryCustom {

    List<CandleDataDocument> findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeDesc(
            String symbol, String intervalCode, Long openTimeStart, Long openTimeEnd, Pageable pageable);

    List<CandleDataDocument> findBySymbolAndIntervalCodeAndOpenTimeBetweenAndExchangeOrderByOpenTimeDesc(
            String symbol, String intervalCode, Long openTimeStart, Long openTimeEnd, String exchange, Pageable pageable);

    boolean existsBySymbolAndIntervalCodeAndOpenTimeAndExchange(
            String symbol, String intervalCode, Long openTime, String exchange);
}
