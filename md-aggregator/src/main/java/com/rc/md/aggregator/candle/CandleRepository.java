package com.rc.md.aggregator.candle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    Optional<CandleEntity> findBySymbolAndIntervalSecAndBucketStart(
            String symbol,
            Integer intervalSec,
            OffsetDateTime bucketStart
    );
}
