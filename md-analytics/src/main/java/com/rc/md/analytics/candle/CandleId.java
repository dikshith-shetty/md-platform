package com.rc.md.analytics.candle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CandleId implements Serializable {
    private String symbol;
    private int intervalSec;
    private OffsetDateTime bucketStart;
}
