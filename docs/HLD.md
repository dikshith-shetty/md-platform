
# MD Platform – High Level Design (HLD)

---

## 1. System Overview

The **MD Platform** is a modular market data system designed to:

- Ingest **bid/ask events** from multiple sources (Binance, simulators, other exchanges).
- Normalize events into a common schema: `BidAskEvent`.
- Transport events via **Kafka** on a normalized topic.
- Aggregate them into **OHLCV candles** over configurable intervals.
- Persist time-series data in **TimescaleDB**.
- Expose historical candle data through a REST API.
- Provide **load-testing**, **logging**, and **distributed tracing** for observability and performance tuning.

This document describes the **high-level architecture** like components, data flow, responsibilities, tech stacks.

---

## 2. High-Level Architecture

### 2.1 Component Diagram (Conceptual)

**Producers / Collectors:**
- `md-collector-binance` (real-time from Binance WS)
- `md-collector-simulator` (internal simulator for testing)

**Messaging Backbone:**
- **Kafka** (topic: `md.bidask.normalized`)

**Processing / Storage:**
- `md-aggregator` (candle builder)
- **TimescaleDB** (PostgreSQL with time-series extensions)

**Serving / Clients / Tools:**
- `md-analytics` (REST API: `/history`)
- `md-analytics-client` (Feign client library)
- `md-load-test` (load / performance testing)

**Cross-Cutting:**
- `config-server` (Spring Cloud Config Server)
- **OpenTelemetry Collector + Jaeger** (traces)
- **Logback + @Slf4j** (structured logging)

---

## 3. Data Flow – End-to-End

### 3.1 From Exchange to Normalized Topic

1. **Binance WebSocket → md-collector-binance**
   - Subscribes to Binance `@bookTicker` WebSocket stream, e.g. `btcusdt@bookTicker`.
   - Parses JSON, extracts:
     - `b` – best bid price
     - `a` – best ask price
     - `T` – event timestamp (ms) from Binance
   - Converts to common data model:

     ```java
     public record BidAskEvent(
         String symbol,
         double bid,
         double ask,
         long timestamp   // UNIX seconds, derived from Binance T/1000
     ) {}
     ```

   - Uses a mapping defined in config:

     ```yaml
     md.collector.binance.symbols:
       btcusdt: BTCUSD
     ```

   - Publishes serialized `BidAskEvent` (JSON) to:
     - Kafka topic: `md.bidask.normalized`
     - Key: `symbol`
     - Value: JSON string

2. **Simulator → md-collector-simulator**
   - Internal synthetic producer for development.
   - Generates random-walk prices per configured symbol.
   - Builds `BidAskEvent` for each tick using `Instant.now()` for timestamp.
   - Publishes to the same normalized topic:
     - `md.bidask.normalized`

**Key design point:**  
All upstream sources must **normalize into the same `BidAskEvent` schema and topic** so downstream components don’t care about exchange-specific formats.

---

### 3.2 Aggregation into Candles

**Service:** `md-aggregator`  
**Input:** Kafka topic `md.bidask.normalized`  
**Output:** TimescaleDB candles

1. **Kafka Consumer**
   - Listens on `md.bidask.normalized` with a consumer group (e.g. `md-aggregator`).
   - Deserializes JSON into `BidAskEvent`.

2. **Interval Configuration**
   - Aggregator supports **configurable intervals** via properties (and later config server):

     ```yaml
     md.aggregator.intervals:
       - id: "1m"
         seconds: 60
       - id: "5m"
         seconds: 300
     ```

3. **Bucket Calculation**
   - For each event:
     - Compute bucket start time (in seconds) per interval:
       ```java
       bucketSec = (timestamp / intervalSec) * intervalSec;
       ```
     - Convert to `OffsetDateTime` in UTC.

4. **Candle Update Logic**
   - For each (symbol, interval, bucketStart):
     - Load existing candle or create new:
       ```java
       open  = first price in bucket
       high  = max(price)
       low   = min(price)
       close = last price in bucket
       volume = count of ticks (synthetic volume)
       ```
     - Price used = mid price:
       ```java
       mid = (bid + ask) / 2
       ```
   - Persist candles to TimescaleDB via JPA entity `CandleEntity`.

5. **Storage Model**
   - Table `candles` (TimescaleDB), columns:
     - symbol, interval_sec, bucket_start, open, high, low, close, volume
   - Indexed by (symbol, interval_sec, bucket_start) for efficient querying.

---

### 3.3 Analytics – `/api/v1/history` Endpoint

**Service:** `md-analytics`  
**Input:** TimescaleDB `candles` table  
**Output:** HTTP JSON response for OHLCV queries

1. Accepts request:

   ```http
   GET /api/v1/history?symbol=BTC-USD&interval=1m&from=1700000000&to=1700003600
   ```

2. Validates:
   - `from <= to`
   - `interval` is supported same as interval configuration used in aggregator.

3. Resolves interval ID to seconds using `IntervalConfig` & `IntervalDefinition`.

4. Queries DB:

   ```java
   findBySymbolAndIntervalSecAndBucketStartBetweenOrderByBucketStartAsc(
       symbol, intervalSec, fromTs, toTs
   );
   ```

5. Builds **Time-Series JSON** in the format:

   ```json
   {
     "s": "ok",
     "t": [1620000000, 1620000060, ...],
     "o": [29500.5, 29501.0, ...],
     "h": [29510.0, 29505.0, ...],
     "l": [29490.0, 29500.0, ...],
     "c": [29505.0, 29502.0, ...],
     "v": [10, 8, ...]
   }
   ```

   If there is an error / invalid input:

   ```json
   {
     "s": "error",
     "message": "Unsupported interval: 2m"
   }
   ```

6. Swagger / OpenAPI is enabled via `springdoc-openapi`, allowing:
   - `/swagger-ui.html`
   - `/v3/api-docs`

---

### 3.4 Load Testing Flow

**Service:** `md-load-test`  
**Purpose:** Empirically measure **latency** and **throughput** of `/api/v1/history` endpoint.

1. Reads configuration (`md.load.*`):
   - number of threads
   - requests per thread
   - symbol, interval, from, to
2. Uses Feign `AnalyticsClient` (`md-analytics-client`) to call `md-analytics`.
3. Spawns worker threads, each making many `/api/v1/history` calls.
4. Records per-request latency in milliseconds.
5. Produces statistics:
   - total time, total requests
   - throughput (requests/second)
   - latency min / avg / max / p50 / p95 / p99
   - error summary

This is a **system-level load harness**, not a unit test.

---

## 4. Technology Stack

### 4.1 Backend

- **Language:** Java 17
- **Framework:** Spring Boot 3.x
- **Messaging:** Apache Kafka (Confluent images for local dev)
- **Database:** TimescaleDB (PostgreSQL)
- **Config:** Spring Cloud Config Server (planned to externalize app configs)
- **Client:** Spring Cloud OpenFeign (`md-analytics-client`)
- **Logging:** Logback + Lombok `@Slf4j`
- **Tracing/Telemetry:**
  - Micrometer Tracing
  - OpenTelemetry OTLP exporter
  - OTel Collector + Jaeger

### 4.2 Testing & Tools

- JUnit 5
- Spring Boot Test
- Testcontainers (PostgreSQL, Kafka)
- Embedded Kafka (Spring Kafka Test)
- Maven build with a multi-module structure

---

## 5. Cross-Cutting Concerns

### 5.1 Observability

- **Distributed Tracing**:
  - All services export traces to OTel Collector (`http://localhost:4317`).
  - Jaeger UI used for trace visualization (`http://localhost:16686`).
  
- **Logging**:
  - Pattern includes `traceId` and `spanId` for log-trace correlation.
  - Logs go to:
    - Console
    - Rolling files in `logs/` per service.

### 5.2 Configuration Management

- A dedicated `config-server` module is present.
- The plan is to:
  - Move environment-specific properties to a Git-backed config repo.
  - Clients (`md-aggregator`, `md-analytics`, etc.) consume from `config-server`.
- For v1, most configs are still local `application.yml` files, but structured for easy migration.

### 5.3 Scalability

- **Collectors**:
  - Can scale horizontally per exchange/symbol.
  - Binance collector uses per-symbol WebSocket threads (cached thread pool).

- **Kafka**:
  - Normalized topic can be partitioned to distribute the load.
  - Consumer groups allow scaling aggregator instances.

- **Aggregator**:
  - Stateless apart from DB; multiple instances can consume partitions.
  - TimescaleDB handles time-series storage efficiently.

- **Analytics**:
  - Stateless API layer; can be scaled behind a load balancer / gateway.

---

## 6. Non-Functional Requirements

### 6.1 Performance

- **Target:** Low-latency ingestion and reasonable response times under moderate load.
- `md-load-test` is used to:
  - Benchmark `/api/v1/history` endpoint.
  - Provide latency percentiles and throughput.

### 6.2 Reliability

- Binance collector uses:
  - Reconnection loop with configurable `reconnectDelaySeconds`.
  - Simple backoff between reconnections.
- Aggregator and analytics rely on:
  - Kafka as durable buffer.
  - TimescaleDB for persistent storage.

### 6.3 Observability / Debuggability

- Standardized log format with correlation IDs.
- Tracing to understand:
  - Where time is spent
  - Which component is bottlenecking

### 6.4 Extensibility

The design anticipates:

- Additional collectors (Bybit, OKX, etc.) → all normalize to `BidAskEvent`.
- Additional event types (e.g. `TradeEvent`, `DepthEvent`) in future
- New aggregation logic (e.g., volume-weighted prices, custom metrics).

---

## 7. Future Design Extensions

These are **not implemented yet**, but the architecture keeps them in mind:

1. **Multiple Exchanges**
   - Separate collectors per exchange.
   - All are normalizing to a shared schema or versioned schema.

2. **Depth Snapshots & Incremental Order Book Updates**
   - Maintain full in-memory order books.
   - Emit more detailed events for advanced analytics.

3. **Replay / Backtest Mode**
   - Service that reads from historical Kafka partitions.
   - Replays normalized events at configurable speeds.

4. **Archival**
   - Offload older data to:
     - Object storage (S3-compatible)
     - Columnar formats (e.g., Parquet)

5. **Security**
   - Authentication & authorization for `/api/v1/history` and future APIs.
   - Rate limiting and tenant isolation.

---

## 8. Related Documents

- [**Low Level Design (LLD)**](LLD.md) – Detailed class diagrams, DB schema, method-level design.

---
