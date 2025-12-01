package com.rc.md.loadtest;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
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
     * Symbol to query, e.g. BTC-USD.
     */
    private String symbol = "BTC-USD";

    /**
     * Interval id to query, e.g. 1m.
     */
    private String interval = "1m";

    /**
     * From timestamp (UNIX seconds).
     */
    private long from = 1764470000;

    /**
     * To timestamp (UNIX seconds).
     */
    private long to = 1764480000;

    /**
     * Whether to print each error in detail.
     */
    private boolean verboseErrors = false;

}
