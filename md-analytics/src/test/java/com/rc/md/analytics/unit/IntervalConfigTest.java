package com.rc.md.analytics.unit;

import com.rc.md.analytics.config.IntervalConfig;
import com.rc.md.common.config.IntervalDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalConfigTest {

    @Test
    void findByIdReturnsCorrectInterval() {
        IntervalDefinition d1 = new IntervalDefinition();
        d1.setId("1m");
        d1.setSeconds(60);

        IntervalDefinition d5 = new IntervalDefinition();
        d5.setId("5m");
        d5.setSeconds(300);

        IntervalConfig cfg = new IntervalConfig();
        cfg.setIntervals(List.of(d1, d5));

        assertThat(cfg.findById("1m")).isNotNull();
        assertThat(cfg.findById("1m").getSeconds()).isEqualTo(60);

        assertThat(cfg.findById("5m")).isNotNull();
        assertThat(cfg.findById("5m").getSeconds()).isEqualTo(300);

        assertThat(cfg.findById("15m")).isNull();
    }
}
