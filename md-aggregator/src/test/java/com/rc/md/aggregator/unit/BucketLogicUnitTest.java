package com.rc.md.aggregator.unit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BucketLogicUnitTest {

    long bucket(long tsSeconds, int intervalSec) {
        return (tsSeconds / intervalSec) * intervalSec;
    }

    @Test
    void bucketAlignsToIntervalBoundaries() {
        assertThat(bucket(1700000037L, 60)).isEqualTo(1699999980L); // just sanity check pattern
        // More explicit examples:
        assertThat(bucket(60L, 60)).isEqualTo(60L);
        assertThat(bucket(61L, 60)).isEqualTo(60L);
        assertThat(bucket(119L, 60)).isEqualTo(60L);
        assertThat(bucket(120L, 60)).isEqualTo(120L);

        assertThat(bucket(300L, 300)).isEqualTo(300L);
        assertThat(bucket(301L, 300)).isEqualTo(300L);
    }

    @Test
    void bucketForDifferentIntervals() {
        assertThat(bucket(125L, 30)).isEqualTo(120L);
        assertThat(bucket(59L, 30)).isEqualTo(30L);
        assertThat(bucket(29L, 30)).isEqualTo(0L);
    }
}
