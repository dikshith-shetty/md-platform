package com.rc.md.aggregator.config;

import com.rc.md.common.config.IntervalDefinition;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "md.aggregator")
public class AggregatorProperties {
    private List<IntervalDefinition> intervals;
}
