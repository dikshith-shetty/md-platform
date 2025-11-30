# MD Platform – Market Data Processing System

This repository contains a modular **market data pipeline** built using **Java 17+, Spring Boot 3**, designed for real‑time bid/ask ingestion, candle aggregation, analytics queries, load testing, and distributed tracing support.

---

# 📦 Modules Overview

| Module | Description |
|--------|-------------|
| **md-collector-binance** | Connects to Binance WebSocket @bookTicker stream, normalizes bid/ask events using exchange timestamps, publishes to Kafka. |
| **md-collector-simulator** | Generates synthetic bid/ask events for development and load testing. |
| **md-aggregator** | Consumes normalized events, buckets them into candles (configurable intervals), stores in TimescaleDB. |
| **md-analytics** | Exposes REST API (**/history**) to fetch OHLCV candle data. |
| **md-analytics-client** | Feign client jar to integrate analytics APIs in other services. |
| **md-load-test** | CLI tool to load-test md-analytics API for latency & throughput. |
| **config-server** | Spring Cloud Config Server for centralized configuration. |
| **md-common** | Shared models (records), configs, DTOs. |

---

# 🚀 How to Run the Entire System (Local Development)

## **Step 1 — Start Infra (Kafka, TimescaleDB, OTel Collector, Jaeger, Kafka UI)**

```bash
cd infra
docker compose up -d
```

Infra services exposed:

- Kafka: **localhost:9093**
- TimescaleDB: **localhost:5432**
- Kafka UI: **http://localhost:8085**
- OTel Collector (OTLP): **localhost:4317**
- Jaeger UI: **http://localhost:16686**

---

## **Step 2 — Start Spring Cloud Config Server**

```bash
cd config-server
mvn spring-boot:run
```

---

## **Step 3 — Start Aggregator and Analytics Services**

```bash
cd md-aggregator
mvn spring-boot:run

cd ../md-analytics
mvn spring-boot:run
```

---

## **Step 4: Start Collector**

### 1. Binance Bid and Ask event data

```bash
cd md-collector-binance
mvn spring-boot:run
```

Configured under:

```yaml
md.collector.binance.symbols:
  btcusdt: BTCUSD
```

### 2. Simulated Bid and Ask event Data

```bash
cd md-collector-simulator
mvn spring-boot:run
```

---

## **Step 5 — Query Candle History**

Example:

```
GET http://localhost:8080/api/v1/history?symbol=BTCUSD&interval=1m&from=1700000000&to=1700000600
```
replace `from` and `to` values with the latest timestamp in seconds. 

---

## **Step 6: Run Load Test**

```bash
cd md-load-test
mvn spring-boot:run
```

or with custom parameters:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--md.load.threads=20 --md.load.requestsPerThread=200"
```

---

# 🧪 Testing

The repo includes:

- **Unit tests** (bucket logic, interval config, validation)
- **Integration tests (Testcontainers)**:
  - Kafka → Aggregator → TimescaleDB
  - Analytics → REST → TimescaleDB

Run all tests:

```bash
mvn test
```

---

# 🛠 Assumptions Made in v1**

### ✔ Timestamp Source  
We use **Binance event timestamp (“T”)** (ms → sec) for candle alignment.  
Fallback: `Instant.now()` only if “T” missing.

### ✔ Normalized Topic  
All collectors emit **BidAskEvent** into:

```
md.bidask.normalized
```

### ✔ Candle Storage  
Stored in TimescaleDB as:

```java
record Candle(long timestamp, double open, double high, double low, double close, long volume)
```

### ✔ Configurable Intervals  
Aggregator supports **any** interval via config server:

```
1m, 5m, 15m, 1h, ...
```

### ✔ Distributed Tracing  
All services send traces to OTel Collector → Jaeger.

### ✔ Logging  
Using `@Slf4j`, logs to console + rolling files.

---

# 📄Documents 

### [**High-Level Design**](./docs/HLD.md)  
### [**Low-Level Design**](./docs/LLD.md)  

---


