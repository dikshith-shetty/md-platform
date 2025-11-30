package com.rc.md.collector.binance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "md.collector.binance")
public class BinanceCollectorProperties {


    private Map<String, String> symbols;

    private int reconnectDelaySeconds = 5;

    private String wsBase;

}
