package com.rc.md.analytics.candle;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@IdClass(CandleId.class)
@Entity
@Table(name = "candles")
public class CandleEntity {

    @Id
    private String symbol;

    @Id
    private Integer intervalSec;

    @Id
    private OffsetDateTime bucketStart;

    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
