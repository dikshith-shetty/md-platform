
# MD Platform – Test Plan

Version: v1  
Author: ChatGPT (with Dikshith’s guidance)  
Status: Draft / First Cut

---

## 1. Introduction

This **Test Plan** describes the testing strategy for the **MD Platform**, a market data pipeline that:

- Ingests bid/ask events from Binance and simulators.
- Normalizes them into `BidAskEvent` via Kafka.
- Aggregates events into OHLCV candles in TimescaleDB.
- Serves historical data via `/history` endpoint.
- Provides a load testing tool for latency and throughput measurements.

This document defines:

- Test objectives and scope
- Test types and levels
- Environments and dependencies
- Concrete test scenarios (functional and non-functional)
- Basic acceptance criteria

For architecture and design, refer to **HLD.md** and **LLD.md**.  
For run instructions, see **README.md**.

---

## 2. Test Objectives

1. Verify **functional correctness** of:
   - Event ingestion
   - Normalization
   - Aggregation
   - Data retrieval

2. Ensure **non-functional** goals:
   - Acceptable latency and throughput
   - Stability under load
   - Robust handling of disconnections and invalid data

3. Validate **observability**:
   - Logs are useful and consistent
   - Traces exist across key flows (collector → aggregator → analytics → client)

---

## 3. Test Scope

### 3.1 In Scope

- Collectors:
  - `md-collector-binance`
  - `md-collector-simulator`
- Messaging:
  - Kafka topic `md.bidask.normalized`
- Aggregation:
  - `md-aggregator` (Kafka → TimescaleDB)
- Serving:
  - `md-analytics` (`/history`)
- Client & Tools:
  - `md-analytics-client` (Feign)
  - `md-load-test` (load testing)
- Cross-Cutting:
  - Logging & tracing
  - Basic configuration

### 3.2 Out of Scope (for v1)

- Multi-region deployment behavior
- Advanced security (authn/z, rate-limiting)
- Deep exchange-specific validations (e.g., full order book correctness)
- Long-term data archival and replay

---

## 4. Test Levels and Types

### 4.1 Unit Tests

- **Goal:** Validate individual classes / methods in isolation.
- **Framework:** JUnit 5, AssertJ, Mockito.

**Key targets:**

- Bucket logic (timestamp → bucket start).
- Interval configuration lookup.
- Input validation in `/history` (e.g., invalid intervals, from > to).
- Any pure transformation logic.

### 4.2 Integration Tests

- **Goal:** Verify interaction between multiple components/modules, including external dependencies (DB, Kafka).
- **Framework:** Spring Boot Test, Testcontainers, Embedded Kafka.

**Key targets:**

- Aggregator: Kafka → CandleEntity in DB.
- Analytics: DB → `/history` JSON response.

### 4.3 System / End-to-End Tests (Local)

- **Goal:** Validate the full pipeline, end-to-end, in a local/dev environment.
- **Tools:**
  - Docker Compose for infra
  - Manual and scripted tests with curl / Postman
  - Kafka UI, DB client

**Targets:**

- Binance or simulator → Kafka → Aggregator → DB → `/history`.

### 4.4 Performance & Load Tests

- **Goal:** Measure latency and throughput for `/history`.
- **Tool:** `md-load-test` module using Feign client.

**Metrics:**

- Latency distribution (min, avg, max, p50, p95, p99).
- Throughput (requests/second).
- Error rate under load.

### 4.5 Resilience / Error Handling Tests

- **Goal:** Verify system behavior under adverse conditions.
- **Examples:**
  - Kafka unavailable
  - TimescaleDB unavailable
  - Network blips for WebSocket
  - Invalid JSON messages

---

## 5. Test Environment

### 5.1 Local Dev Environment

- OS: Developer machine (Windows / Linux / macOS).
- Java: 17
- Tools:
  - Maven
  - Docker & Docker Compose
- Infra (via `infra/docker-compose.yml`):
  - Kafka + Zookeeper
  - TimescaleDB (PostgreSQL)
  - Kafka UI
  - OpenTelemetry Collector
  - Jaeger

### 5.2 Test Data

- **Simulator:**
  - Synthetic prices with random walk.
  - Configurable symbols, tick intervals.

- **Binance:**
  - Live `@bookTicker` data for selected pairs (e.g., BTCUSDT).
  - Only used for manual/system-level tests (not automated CI).

---

## 6. Detailed Test Scenarios

### 6.1 Unit Test Scenarios

#### 6.1.1 Bucket Logic

- **Component:** Aggregator bucket calculation.
- **Test Cases:**
  1. For interval 60s:
     - `ts=60` → bucket `60`
     - `ts=61` → bucket `60`
     - `ts=119` → bucket `60`
     - `ts=120` → bucket `120`
  2. For interval 300s:
     - `ts=300` → bucket `300`
     - `ts=301` → bucket `300`
  3. Edge cases with 0 and small ts.

- **Expected:** `(ts / intervalSec) * intervalSec` is applied consistently.

#### 6.1.2 IntervalConfig

- **Component:** `IntervalConfig` in analytics.
- **Test Cases:**
  1. Known intervals: `"1m"` → 60, `"5m"` → 300.
  2. Unknown interval: `"15m"` → `null`.

#### 6.1.3 HistoryController Validation

- **Component:** `HistoryController`.
- **Test Cases:**
  1. `from > to` → `s="error"`, message about invalid range.
  2. Unsupported interval → `s="error"`, message about unsupported interval.
  3. Missing/blank symbol → optional future validation (if added).

---

### 6.2 Integration Test Scenarios

#### 6.2.1 Aggregator Integration (Kafka + DB)

- **Setup:**
  - Start PostgreSQL Testcontainer.
  - Start Embedded Kafka.
  - Boot `md-aggregator` in test mode.

- **Steps:**
  1. Build a `BidAskEvent(symbol="BTCUSD", bid=100.0, ask=102.0, timestamp=ts)`.
  2. Serialize to JSON, produce to `md.bidask.normalized`.
  3. Poll `CandleRepository` until a candle exists for:
     - symbol `BTCUSD`
     - interval 60s
     - bucket computed from `ts`.

- **Assertions:**
  - Candle is non-null.
  - `open == close == (100.0 + 102.0)/2`.
  - `volume == 1`.

#### 6.2.2 Analytics Integration (DB + /history)

- **Setup:**
  - Start PostgreSQL Testcontainer.
  - Boot `md-analytics`.
  - Use `CandleRepository` to insert synthetic candles.

- **Steps:**
  1. Insert `CandleEntity` for `symbol="BTCUSD"`, `intervalSec=60`, `bucketStart=bucket`.
  2. Call `/history` with:
     - `symbol=BTCUSD`
     - `interval=1m`
     - `from = bucket - 60`
     - `to = bucket + 60`
  3. Use MockMvc to verify response.

- **Assertions:**
  - HTTP status `200 OK`.
  - JSON `s="ok"`.
  - First entries of `t`, `o`, `h`, `l`, `c`, `v` match the inserted candle.

---

### 6.3 System / End-to-End Test Scenarios

#### 6.3.1 End-to-End with Simulator

- **Setup:**
  1. Start infra via `infra/docker-compose.yml`.
  2. Start `config-server`.
  3. Start `md-aggregator`, `md-analytics`.
  4. Start `md-collector-simulator`.

- **Steps:**
  1. Let simulator run for N minutes to populate candles.
  2. Query `/history` for `symbol` and interval used by simulator.
  3. Observe results.

- **Assertions:**
  - Non-empty arrays in `HistoryResponse`.
  - Times are roughly in the expected range.
  - Prices show reasonable progression over time.

#### 6.3.2 End-to-End with Binance

- **Setup:**
  1. Same as above, but start `md-collector-binance` instead of (or in addition to) simulator.
  2. Configure `md.collector.binance.symbols` (e.g., `btcusdt: BTCUSD`).

- **Steps:**
  1. Let collector run for some time.
  2. Query `/history` for `symbol=BTCUSD`.
  3. Confirm candles are being built.

- **Assertions:**
  - `s="ok"` and non-empty `t` array.
  - No continuous errors in collector logs (e.g., JSON parse or WS errors).

---

### 6.4 Performance & Load Test Scenarios

#### 6.4.1 Basic Load Test

- **Setup:**
  - Ensure infra + aggregator + analytics are running.
  - Ensure some candles exist in DB.

- **Steps:**
  1. Configure `md-load-test` with:
     - `threads=10`
     - `requestsPerThread=100`
     - `symbol=BTCUSD`
     - `interval=1m`
     - `from/to` that match existing data.
  2. Run `md-load-test`.

- **Metrics / Assertions:**
  - Throughput: e.g., > X req/s on dev machine (define X based on hardware).
  - Latency:
    - p95 < Y ms (define Y target, e.g., < 100ms).
  - Error rate: ideally 0.

#### 6.4.2 Stress Test

- **Steps:**
  1. Increase threads and requests per thread (e.g., 50 threads × 1000).
  2. Run again and monitor:
     - CPU usage
     - DB performance
     - Kafka and service logs

- **Observations:**
  - APIs should remain responsive, though latency may increase.
  - No unhandled exceptions or crashes.

---

### 6.5 Resilience & Error Handling Tests

#### 6.5.1 Kafka Unavailable (Aggregator)

- **Scenario:**
  - Stop Kafka container while aggregator is running.
  - Produce events before/after stop.

- **Expected:**
  - Aggregator logs connection/consumer errors.
  - No crash loop (depending on configuration).
  - On Kafka restart, aggregator should resume consuming.

*(In practice, this is often validated manually or with partial automation.)*

#### 6.5.2 TimescaleDB Unavailable (Aggregator or Analytics)

- **Scenario (Aggregator):**
  - Stop TimescaleDB while aggregator is consuming.
  - Observe what happens when trying to persist candles.

- **Expected:**
  - Persistence failures should be logged.
  - Aggregator should not crash uncontrollably.
  - Behavior for retries vs. message loss is documented (v1 may be best-effort).

- **Scenario (Analytics):**
  - Stop DB and hit `/history`.

- **Expected:**
  - Error handling (5xx or error `HistoryResponse`) with logs, no crash.

#### 6.5.3 WebSocket Disconnects (Binance Collector)

- **Scenario:**
  - Intentionally break network / force WS close (or let Binance close idle connection).

- **Expected:**
  - WS `onClose` / `onError` logs.
  - `connectLoop` reconnects after configured `reconnectDelaySeconds`.

---

## 7. Observability Tests

### 7.1 Logging

- Verify that:

  - All services produce logs with pattern containing:
    - `traceId`
    - `spanId`
  - Key events are logged:
    - Collector connected/disconnected
    - Processing errors
    - Aggregation operations (in debug mode if needed)

### 7.2 Tracing

- With OTel Collector and Jaeger running:

  - Trigger end-to-end flow:
    - Event ingestion → Candle creation → `/history` query.
  - Inspect Jaeger:
    - Confirm spans exist for:
      - `md-collector-binance` or `md-collector-simulator`
      - `md-aggregator`
      - `md-analytics`
      - `md-load-test` (if using load-tool)
    - Verify that spans are linked via `traceId`.

---

## 8. Test Data Management

- **Unit tests:** Use synthetic, in-memory or embedded data.
- **Integration tests:** Use Testcontainers with isolated, throwaway DB and Kafka instances.
- **System tests:** Use docker-compose environment; DB may be reset manually between runs if needed.
- No sensitive / production data is used in tests.

---

## 9. Acceptance Criteria

For **v1** of the MD Platform, we consider the system acceptable if:

1. **Unit and integration tests** (`mvn test`) pass consistently.
2. **End-to-end flow** from collector → aggregator → analytics works in local dev.
3. `/history` returns expected OHLCV sequences for both synthetic and Binance-driven tests.
4. **Load-test** shows:
   - Stable behavior
   - Acceptable latency (p95 within agreed bound for the test machine)
   - No unhandled exceptions or resource exhaustion.
5. Logs and traces are sufficient to debug most data flow issues.

---

## 10. Future Enhancements to Test Plan

- Automated CI pipeline integration (run unit + integration tests on each commit).
- Scheduled smoke tests in a shared dev environment.
- Canary tests for new versions (blue/green deployment).
- More detailed chaos/resilience scenarios (e.g., random restarts, partial outages).
- Security testing (authentication, authorization, rate limiting).

---
