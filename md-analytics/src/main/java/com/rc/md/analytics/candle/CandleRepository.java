package com.rc.md.analytics.candle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    List<CandleEntity> findBySymbolAndIntervalSecAndBucketStartBetweenOrderByBucketStartAsc(
            String symbol,
            Integer intervalSec,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
