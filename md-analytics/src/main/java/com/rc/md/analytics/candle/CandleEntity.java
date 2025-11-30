package com.rc.md.analytics.candle;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "candles")
public class CandleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    @Column(name = "interval_sec")
    private Integer intervalSec;

    @Column(name = "bucket_start")
    private OffsetDateTime bucketStart;

    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public Integer getIntervalSec() { return intervalSec; }
    public void setIntervalSec(Integer intervalSec) { this.intervalSec = intervalSec; }
    public OffsetDateTime getBucketStart() { return bucketStart; }
    public void setBucketStart(OffsetDateTime bucketStart) { this.bucketStart = bucketStart; }
    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }
    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }
    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }
    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }
    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }
}
