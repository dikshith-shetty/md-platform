package com.rc.md.loadtest;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "md.load")
public class LoadTestProperties {

    /**
     * Number of parallel worker threads generating load.
     */
    private int threads = 10;

    /**
     * Number of requests per thread.
     */
    private int requestsPerThread = 100;

    /**
     * Symbol to query, e.g. BTCUSD.
     */
    private String symbol = "BTCUSD";

    /**
     * Interval id to query, e.g. 1m.
     */
    private String interval = "1m";

    /**
     * From timestamp (UNIX seconds).
     */
    private long from = 1_700_000_000L;

    /**
     * To timestamp (UNIX seconds).
     */
    private long to = 1_700_000_600L;

    /**
     * Whether to print each error in detail.
     */
    private boolean verboseErrors = false;

    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }
    public int getRequestsPerThread() { return requestsPerThread; }
    public void setRequestsPerThread(int requestsPerThread) { this.requestsPerThread = requestsPerThread; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }
    public long getFrom() { return from; }
    public void setFrom(long from) { this.from = from; }
    public long getTo() { return to; }
    public void setTo(long to) { this.to = to; }
    public boolean isVerboseErrors() { return verboseErrors; }
    public void setVerboseErrors(boolean verboseErrors) { this.verboseErrors = verboseErrors; }
}
