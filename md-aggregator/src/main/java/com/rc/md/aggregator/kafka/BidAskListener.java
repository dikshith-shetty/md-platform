package com.rc.md.aggregator.kafka;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rc.md.aggregator.candle.CandleEntity;
import com.rc.md.aggregator.candle.CandleRepository;
import com.rc.md.aggregator.config.AggregatorProperties;
import com.rc.md.common.config.IntervalDefinition;
import com.rc.md.common.model.BidAskEvent;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@Slf4j
public class BidAskListener {
private final ObjectMapper objectMapper;
    private final CandleRepository candleRepository;
    private final List<IntervalDefinition> intervals;

    public BidAskListener(CandleRepository candleRepository,
                          AggregatorProperties properties) {
        this.objectMapper = new ObjectMapper();
        this.candleRepository = candleRepository;
        this.intervals = properties.getIntervals();
    }

    @KafkaListener(
            topics = "${topics.normalized:md.bidask.normalized}"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            String json = record.value();
            BidAskEvent event = objectMapper.readValue(json, BidAskEvent.class);

            double mid = (event.bid() + event.ask()) / 2.0;
            String symbol = event.symbol();
            long ts = event.timestamp();

            for (IntervalDefinition def : intervals) {
                updateCandle(symbol, mid, ts, def);
            }

        } catch (Exception e) {
            log.error("Failed to process record: {}", record.value(), e);
        }
    }

    private void updateCandle(String symbol, double price, long tsSeconds, IntervalDefinition def) {
        int sec = def.getSeconds();
        long bucketSec = (tsSeconds / sec) * sec;

        OffsetDateTime bucketStart = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(bucketSec), ZoneOffset.UTC);

        CandleEntity candle = candleRepository
                .findBySymbolAndIntervalSecAndBucketStart(symbol, sec, bucketStart)
                .orElseGet(() -> {
                    CandleEntity c = new CandleEntity();
                    c.setSymbol(symbol);
                    c.setIntervalSec(sec);
                    c.setBucketStart(bucketStart);
                    c.setOpen(price);
                    c.setHigh(price);
                    c.setLow(price);
                    c.setClose(price);
                    c.setVolume(0);
                    return c;
                });

        candle.setHigh(Math.max(candle.getHigh(), price));
        candle.setLow(Math.min(candle.getLow(), price));
        candle.setClose(price);
        candle.setVolume(candle.getVolume() + 1);

        candleRepository.save(candle);

        log.debug("Updated candle symbol={} interval={} bucket={} close={} volume={}",
                symbol, sec, bucketStart, candle.getClose(), candle.getVolume());

    }
}
