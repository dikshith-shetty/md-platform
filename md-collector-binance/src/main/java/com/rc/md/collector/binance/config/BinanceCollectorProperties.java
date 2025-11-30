package com.rc.md.collector.binance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "md.collector.binance")
public class BinanceCollectorProperties {

    /**
     * Map of Binance stream symbols (lowercase, e.g. btcusdt) to internal symbols (e.g. BTCUSD).
     */
    private Map<String, String> symbols;

    /**
     * Delay in seconds before reconnecting a WebSocket after it closes or fails.
     */
    private int reconnectDelaySeconds = 5;

    public Map<String, String> getSymbols() {
        return symbols;
    }

    public void setSymbols(Map<String, String> symbols) {
        this.symbols = symbols;
    }

    public int getReconnectDelaySeconds() {
        return reconnectDelaySeconds;
    }

    public void setReconnectDelaySeconds(int reconnectDelaySeconds) {
        this.reconnectDelaySeconds = reconnectDelaySeconds;
    }
}
