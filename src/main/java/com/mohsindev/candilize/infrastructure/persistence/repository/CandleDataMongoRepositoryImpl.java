package com.mohsindev.candilize.infrastructure.persistence.repository;

import com.mohsindev.candilize.infrastructure.persistence.document.CandleDataDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CandleDataMongoRepositoryImpl implements CandleDataMongoRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<String> findDistinctIntervalCodesBySymbol(String symbol) {
        return mongoTemplate.findDistinct(
                Query.query(Criteria.where("symbol").is(symbol)),
                "intervalCode",
                CandleDataDocument.class,
                String.class
        );
    }
}
