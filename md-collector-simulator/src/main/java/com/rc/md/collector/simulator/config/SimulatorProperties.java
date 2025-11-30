package com.rc.md.collector.simulator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "md.collector.simulator")
public class SimulatorProperties {

    private Map<String, Double> symbols;
    private int targetRatePerSymbol;
    private int workerThreads;
    private String topicName;

}
