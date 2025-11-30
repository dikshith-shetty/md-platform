package com.rc.md.collector.binance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rc.md.collector.binance.config.BinanceCollectorProperties;
import com.rc.md.common.model.BidAskEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class BinanceWebSocketService {

    @Value("${topics.normalized:md.bidask.normalized}")
    private String topic;

    private final BinanceCollectorProperties properties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;
    private final ExecutorService executor;

    public BinanceWebSocketService(BinanceCollectorProperties properties,
                                   KafkaTemplate<String, String> kafkaTemplate) {
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
        this.executor = Executors.newCachedThreadPool();
    }

    @PostConstruct
    public void start() {
        Map<String, String> symbols = properties.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            log.warn("No Binance symbols configured under md.collector.binance.symbols, Binance collector will be idle.");
            return;
        }

        symbols.forEach((binanceSymbol, internalSymbol) -> {
            String stream = binanceSymbol.toLowerCase() + "@bookTicker";
            executor.submit(() -> connectLoop(stream, internalSymbol));
        });

        log.info("BinanceWebSocketService started for symbols: {}", symbols);
    }

    private void connectLoop(String stream, String internalSymbol) {
        int delay = Math.max(properties.getReconnectDelaySeconds(), 1);
        while (true) {
            try {
                String url = properties.getWsBase() + "/" + stream;
                log.info("Connecting to Binance stream {} as internal symbol {}", url, internalSymbol);

                WebSocket ws = httpClient.newWebSocketBuilder()
                        .buildAsync(URI.create(url), new WebSocketListener(stream, internalSymbol))
                        .join();

                // Block until completion; listener handles lifecycle.
                // When onError/onClose is called, we break and reconnect.
                // We just wait here to avoid a tight loop.
                synchronized (ws) {
                    ws.wait();
                }
            } catch (Exception e) {
                log.error("Error in Binance WebSocket loop for stream {} (symbol {}): {}", stream, internalSymbol, e.toString());
            }

            try {
                log.info("Reconnecting Binance stream {} after {} seconds...", stream, delay);
                Thread.sleep(delay * 1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private class WebSocketListener implements WebSocket.Listener {

        private final String stream;
        private final String internalSymbol;
        private final StringBuilder messageBuffer = new StringBuilder();

        WebSocketListener(String stream, String internalSymbol) {
            this.stream = stream;
            this.internalSymbol = internalSymbol;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            log.info("Binance WebSocket opened for stream {} (symbol {})", stream, internalSymbol);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String json = messageBuffer.toString();
                messageBuffer.setLength(0);
                handleMessage(json);
            }
            webSocket.request(1);
            return null;
        }

        private void handleMessage(String json) {
            try {
                JsonNode node = objectMapper.readTree(json);

                // Binance bookTicker fields of interest:
                // "s" - symbol
                // "b" - best bid price
                // "a" - best ask price
                JsonNode bidNode = node.get("b");
                JsonNode askNode = node.get("a");

                if (bidNode == null || askNode == null || bidNode.isNull() || askNode.isNull()) {
                    log.debug("Skipping message without bid/ask: {}", json);
                    return;
                }

                double bid = Double.parseDouble(bidNode.asText());
                double ask = Double.parseDouble(askNode.asText());
                JsonNode timeNode = node.get("T"); // Binance event time in ms
                long ts;
                if (timeNode != null && !timeNode.isNull()) {
                    ts = timeNode.asLong() / 1000;  // convert ms → sec
                } else {
                    ts = Instant.now().getEpochSecond(); // fallback
                }

                BidAskEvent event = new BidAskEvent(internalSymbol, bid, ask, ts);
                String payload = objectMapper.writeValueAsString(event);

                log.info("Sending message to Kafka: {}", payload);

                ProducerRecord<String, String> record =
                        new ProducerRecord<>(topic, internalSymbol, payload);

                kafkaTemplate.send(record);
            } catch (Exception e) {
                log.warn("Failed to handle Binance message: {}", e.toString());
            }
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("WebSocket error for stream {} (symbol {}): {}", stream, internalSymbol, error.toString());
            synchronized (webSocket) {
                webSocket.notifyAll();
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("WebSocket closed for stream {} (symbol {}): status={} reason={}", stream, internalSymbol, statusCode, reason);
            synchronized (webSocket) {
                webSocket.notifyAll();
            }
            return null;
        }
    }
}
