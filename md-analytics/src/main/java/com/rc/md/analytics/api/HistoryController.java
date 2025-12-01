package com.rc.md.analytics.api;

import com.rc.md.analytics.candle.CandleEntity;
import com.rc.md.analytics.candle.CandleRepository;
import com.rc.md.analytics.config.IntervalConfig;
import com.rc.md.common.api.HistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@RestController
public class HistoryController  {

    private final CandleRepository candleRepository;
    private final IntervalConfig intervalConfig;

    public HistoryController(CandleRepository candleRepository,
                             IntervalConfig intervalConfig) {
        this.candleRepository = candleRepository;
        this.intervalConfig = intervalConfig;
    }

    @Operation(
            summary = "Get historical candles",
            description = "Returns OHLCV candles for a symbol and interval in a given time range."
    )
    @GetMapping("/api/v1/history")
    public HistoryResponse getHistory(
            @Parameter(description = "Symbol, e.g. BTC-USD")
            @RequestParam("symbol") String symbol,
            @Parameter(description = "Interval id, e.g. 1m")
            @RequestParam("interval") String intervalId,
            @Parameter(description = "From timestamp (UNIX seconds)")
            @RequestParam("from") long from,
            @Parameter(description = "To timestamp (UNIX seconds)")
            @RequestParam("to") long to
    ) {
        HistoryResponse res = new HistoryResponse();

        if (from > to) {
            res.setS("error");
            res.setMessage("from must be <= to");
            return res;
        }

        var intervalDef = intervalConfig.findById(intervalId);
        if (intervalDef == null) {
            res.setS("error");
            res.setMessage("Unsupported interval: " + intervalId);
            return res;
        }

        int intervalSec = intervalDef.getSeconds();

        OffsetDateTime fromTs = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(from), ZoneOffset.UTC);
        OffsetDateTime toTs = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(to), ZoneOffset.UTC);

        List<CandleEntity> candles =
                candleRepository.findBySymbolAndIntervalSecAndBucketStartBetweenOrderByBucketStartAsc(
                        symbol, intervalSec, fromTs, toTs);

        List<Long> t = new ArrayList<>(candles.size());
        List<Double> o = new ArrayList<>(candles.size());
        List<Double> h = new ArrayList<>(candles.size());
        List<Double> l = new ArrayList<>(candles.size());
        List<Double> c = new ArrayList<>(candles.size());
        List<Long> v = new ArrayList<>(candles.size());

        for (CandleEntity ce : candles) {
            long epochSec = ce.getBucketStart().toEpochSecond();
            t.add(epochSec);
            o.add(ce.getOpen());
            h.add(ce.getHigh());
            l.add(ce.getLow());
            c.add(ce.getClose());
            v.add(ce.getVolume());
        }

        res.setS("ok");
        res.setT(t);
        res.setO(o);
        res.setH(h);
        res.setL(l);
        res.setC(c);
        res.setV(v);
        return res;
    }
}
