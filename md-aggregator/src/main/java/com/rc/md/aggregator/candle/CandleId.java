package com.rc.md.aggregator.candle;

import java.io.Serializable;
import java.time.OffsetDateTime;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CandleId implements Serializable {
    private String symbol;
    private int intervalSec;
    private OffsetDateTime bucketStart;
}
