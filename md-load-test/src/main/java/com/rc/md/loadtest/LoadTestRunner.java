package com.rc.md.loadtest;

import lombok.extern.slf4j.Slf4j;

import com.rc.md.client.analytics.AnalyticsClient;
import com.rc.md.common.api.HistoryResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class LoadTestRunner {
private final AnalyticsClient analyticsClient;
    private final LoadTestProperties properties;

    public LoadTestRunner(AnalyticsClient analyticsClient,
                          LoadTestProperties properties) {
        this.analyticsClient = analyticsClient;
        this.properties = properties;
    }

    public void runOnce() throws InterruptedException {
        int threads = properties.getThreads();
        int requestsPerThread = properties.getRequestsPerThread();
        String symbol = properties.getSymbol();
        String interval = properties.getInterval();
        long from = properties.getFrom();
        long to = properties.getTo();

        log.info("Starting load test: threads={}, requestsPerThread={}, totalRequests={}, symbol={}, interval={}, from={}, to={}",
                threads, requestsPerThread, threads * requestsPerThread, symbol, interval, from, to);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Double> latenciesMillis = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        long startWall = System.nanoTime();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        long t0 = System.nanoTime();
                        try {
                            HistoryResponse res = analyticsClient.getHistory(symbol, interval, from, to);
                            long t1 = System.nanoTime();
                            double millis = (t1 - t0) / 1_000_000.0;
                            latenciesMillis.add(millis);

                            if (!"ok".equalsIgnoreCase(res.getS())) {
                                errors.add("Non-ok status: " + res.getS() + " msg=" + res.getMessage());
                            }
                        } catch (Exception e) {
                            long t1 = System.nanoTime();
                            double millis = (t1 - t0) / 1_000_000.0;
                            latenciesMillis.add(millis);
                            errors.add("Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                            if (properties.isVerboseErrors()) {
                                log.warn("Request failed", e);
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endWall = System.nanoTime();
        executor.shutdown();

        double totalSeconds = (endWall - startWall) / 1_000_000_000.0;
        int totalRequests = threads * requestsPerThread;
        double throughput = totalRequests / totalSeconds;

        List<Double> sorted = new ArrayList<>(latenciesMillis);
        Collections.sort(sorted);

        DoubleSummaryStatistics stats = sorted.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        double p50 = percentile(sorted, 50);
        double p95 = percentile(sorted, 95);
        double p99 = percentile(sorted, 99);

        log.info("Load test completed: totalSeconds={}, totalRequests={}, throughput={} req/s",
                String.format("%.3f", totalSeconds),
                totalRequests,
                String.format("%.2f", throughput));

        log.info("Latency stats (ms): min={}, avg={}, max={}, p50={}, p95={}, p99={}",
                String.format("%.2f", stats.getMin()),
                String.format("%.2f", stats.getAverage()),
                String.format("%.2f", stats.getMax()),
                String.format("%.2f", p50),
                String.format("%.2f", p95),
                String.format("%.2f", p99));

        if (!errors.isEmpty()) {
            log.warn("There were {} errors during load test (showing up to 5):", errors.size());
            errors.stream().limit(5).forEach(err -> log.warn("  {}", err));
        } else {
            log.info("No errors encountered during load test.");
        }
    }

    private double percentile(List<Double> sorted, double pct) {
        if (sorted.isEmpty()) return 0.0;
        double index = pct / 100.0 * (sorted.size() - 1);
        int i = (int) Math.floor(index);
        int j = (int) Math.ceil(index);
        if (i == j) return sorted.get(i);
        double frac = index - i;
        return sorted.get(i) * (1 - frac) + sorted.get(j) * frac;
    }
}
