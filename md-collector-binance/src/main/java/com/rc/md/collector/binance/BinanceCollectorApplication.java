package com.rc.md.collector.binance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BinanceCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BinanceCollectorApplication.class, args);
    }
}
