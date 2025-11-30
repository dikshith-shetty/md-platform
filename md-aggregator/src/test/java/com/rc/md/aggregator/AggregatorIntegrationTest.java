package com.rc.md.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rc.md.aggregator.candle.CandleEntity;
import com.rc.md.aggregator.candle.CandleRepository;
import com.rc.md.common.model.BidAskEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "md.bidask.normalized")
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class AggregatorIntegrationTest {

    private static final String TOPIC = "md.bidask.normalized";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private CandleRepository candleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAggregateBidAskIntoOneMinuteCandle() throws Exception {
        // given
        String symbol = "BTCUSD";
        long ts = 1_700_000_060L; // arbitrary unix seconds
        double bid = 100.0;
        double ask = 102.0;
        BidAskEvent event = new BidAskEvent(symbol, bid, ask, ts);
        String payload = objectMapper.writeValueAsString(event);

        kafkaTemplate.send(new ProducerRecord<>(TOPIC, symbol, payload));
        kafkaTemplate.flush();

        // when - wait for aggregator to consume and persist the candle
        long bucketSec = (ts / 60) * 60;
        OffsetDateTime bucketStart = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(bucketSec),
                ZoneOffset.UTC
        );

        CandleEntity candle = waitForCandle(symbol, 60, bucketStart, Duration.ofSeconds(10));

        // then
        assertThat(candle).isNotNull();
        double mid = (bid + ask) / 2.0;
        assertThat(candle.getOpen()).isEqualTo(mid);
        assertThat(candle.getHigh()).isEqualTo(mid);
        assertThat(candle.getLow()).isEqualTo(mid);
        assertThat(candle.getClose()).isEqualTo(mid);
        assertThat(candle.getVolume()).isEqualTo(1L);
    }

    private CandleEntity waitForCandle(String symbol,
                                       int intervalSec,
                                       OffsetDateTime bucketStart,
                                       Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<CandleEntity> optional = candleRepository
                    .findBySymbolAndIntervalSecAndBucketStart(symbol, intervalSec, bucketStart);
            if (optional.isPresent()) {
                return optional.get();
            }
            Thread.sleep(200);
        }
        return null;
    }
}
