package com.mohsindev.candilize.infrastructure.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * MongoDB document for one OHLCV candle. Stored in collection "candle_data".
 * Compound unique index on (symbol, intervalCode, openTime, exchange) prevents duplicates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "candle_data")
@CompoundIndexes({
        @CompoundIndex(name = "uq_candle", def = "{'symbol': 1, 'intervalCode': 1, 'openTime': 1, 'exchange': 1}", unique = true)
})
public class CandleDataDocument {

    @Id
    private String id;

    private String symbol;
    private String intervalCode;
    private Long openTime;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal volume;
    private Long closeTime;
    private String exchange;
}
