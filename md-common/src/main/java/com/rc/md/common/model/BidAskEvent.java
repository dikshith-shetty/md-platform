package com.rc.md.common.model;

public record BidAskEvent(
        String symbol,
        double bid,
        double ask,
        long timestamp
) {}
