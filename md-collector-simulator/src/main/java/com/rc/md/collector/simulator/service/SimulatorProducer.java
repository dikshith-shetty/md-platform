package com.rc.md.collector.simulator.service;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rc.md.collector.simulator.config.SimulatorProperties;
import com.rc.md.common.model.BidAskEvent;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class SimulatorProducer {

    @Value( "${topics.normalized:md.bidask.normalized}")
    private String topic;

    private final SimulatorProperties properties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public SimulatorProducer(SimulatorProperties properties,
                             KafkaTemplate<String, String> kafkaTemplate) {
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
    }


    @PostConstruct
    public void start() {
        ExecutorService executor = Executors.newFixedThreadPool(properties.getWorkerThreads());
        for (Map.Entry symbol : properties.getSymbols().entrySet()) {
            executor.submit(() -> runSymbolLoop((String) symbol.getKey(), (Double) symbol.getValue()));
        }
        log.info("Simulator started for symbols {}", properties.getSymbols());
    }

    private void runSymbolLoop(String symbol, Double initialPrice) {
        int randomSeed = ThreadLocalRandom.current().nextInt(1, 51);
        long sleepMillis = 1000L / randomSeed;
        double midPrice = initialPrice;
        while (true) {
            try {
                int[] prices = getPrice(midPrice, randomSeed);
                double bid = prices[0];
                double ask = prices[1];
                long ts = Instant.now().getEpochSecond();
                BidAskEvent event = new BidAskEvent(symbol, bid, ask, ts);
                String json = objectMapper.writeValueAsString(event);
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(topic, symbol, json);

                kafkaTemplate.send(record);
                midPrice = (bid + ask) / 2.0;
                Thread.sleep(sleepMillis);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize event", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("Unexpected error in simulator loop", e);
            }
        }
    }

    private int[] getPrice(double price, int seed) {
        // 1. --- Update Mid-Price (Geometric Brownian Motion) ---
        double mu = 0.1;
        double sigma = 0.2; //volatility.

        double deltaT = seed/1000.0;

        // Z ~ N(0, 1) - Standard Normal Distribution
        double Z = random.nextGaussian();

        // Calculate the exponent term in the GBM formula
        double exponent = (mu - (sigma * sigma / 2)) * deltaT + sigma * Math.sqrt(deltaT) * Z;

        // New Mid-Price
        double P_t_plus_dt = price * Math.exp(exponent);

        // 2. --- Calculate the Bid-Ask Spread ---

        // Spread Noise: A small random component to simulate spread widening/tightening
        // Uniformly distributed between 0 and 0.0001 (0 to 0.01%)
        double spreadNoise = random.nextDouble() * 0.0001;

        // Total Spread Fraction = Base Spread + Noise
        double totalSpreadFraction = 0.0015 + spreadNoise;

        // Spread Value
        double spread = P_t_plus_dt * totalSpreadFraction;

        // 3. --- Determine Bid and Ask ---
        double ask = P_t_plus_dt + spread / 2.0;
        double bid = P_t_plus_dt - spread / 2.0;

        // Round to 2 decimal places (standard for USD pairs)
        ask = Math.round(ask * 100.0) / 100.0;
        bid = Math.round(bid * 100.0) / 100.0;

        return new int[]{(int) bid, (int) ask};
    }
}
