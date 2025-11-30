package com.rc.md.common.model;

public record Candle(
        long time,
        double open,
        double high,
        double low,
        double close,
        long volume
) {}
