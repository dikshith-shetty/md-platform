package com.rc.md.aggregator.candle;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;


@Entity
@Table(name = "candles")
@IdClass(CandleId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
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
