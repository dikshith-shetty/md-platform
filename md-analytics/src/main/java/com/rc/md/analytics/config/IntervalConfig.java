package com.rc.md.analytics.config;

import com.rc.md.common.config.IntervalDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "md.aggregator")
public class IntervalConfig {

    private List<IntervalDefinition> intervals;

    public List<IntervalDefinition> getIntervals() { return intervals; }
    public void setIntervals(List<IntervalDefinition> intervals) { this.intervals = intervals; }

    public IntervalDefinition findById(String id) {
        if (intervals == null) return null;
        return intervals.stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
