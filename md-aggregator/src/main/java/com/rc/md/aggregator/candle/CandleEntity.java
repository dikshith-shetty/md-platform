package com.rc.md.aggregator.candle;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
@Entity
@Table(name = "candles")
public class CandleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String symbol;
    private Integer intervalSec;
    private OffsetDateTime bucketStart;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
