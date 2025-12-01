
# MD Platform – Low Level Design (LLD)

---

## 1. Scope

This **Low Level Design (LLD)** describes the internal structure of the MD Platform:

- Module-wise responsibilities
- Important classes, records, and configurations
- Kafka topics and serialization
- TimescaleDB schema
- REST API contract and Feign client
- Tracing, logging, and testing structure

For architecture overview, see: [**High Level Design**](HLD.md)  
For run instructions, see: [**Read Me**](../README.md)

---

## 2. Modules and Packages

### 2.1 `md-common`

**Purpose:** Shared model and config types.

**Key packages & types:**

- `com.rc.md.common.model`
  - `public record BidAskEvent(String symbol, double bid, double ask, long timestamp) {}`
    - `symbol`: normalized symbol (e.g., `BTC-USD`)
    - `bid`, `ask`: double best bid/ask
    - `timestamp`: UNIX seconds, typically exchange event time
  - `public record Candle(long time, double open, double high, double low, double close, long volume) {}`
    - Value object used for logical/transport representation (not JPA entity).

- `com.rc.md.common.api`
  - `public class HistoryResponse`
    - Fields:
      - `String s` – status (`"ok"` or `"error"`)
      - `List<Long> t` – timestamps
      - `List<Double> o, h, l, c` – OHLC
      - `List<Long> v` – volume
      - `String message` – optional error message
    - Standard getters/setters; used for REST and Feign serialization.

- `com.rc.md.common.config`
  - `public class IntervalDefinition`
    - `String id` – human-friendly ID (`"1m"`, `"5m"`, etc.)
    - `int seconds` – interval in seconds
  - Intended to be used by both `md-aggregator` and `md-analytics`.

---

### 2.2 `config-server`

**Purpose:** Spring Cloud Config Server (baseline, for future externalization of configs).

**Key class:**

- `com.rc.md.configserver.ConfigServerApplication`
  - `@EnableConfigServer`
  - Standard Spring Boot entrypoint.

**Configuration:**

- `src/main/resources/application.yml`:
  - For now, minimal config; in a real setup:
    - Git backend (URI, branch)
    - Application-specific config mappings.

---

### 2.3 `md-collector-binance`

**Purpose:** Connect to Binance WebSocket (`@bookTicker`), convert to `BidAskEvent`, send to Kafka.

**Key packages & types:**

#### 2.3.1 Config

- `com.rc.md.collector.binance.config.BinanceCollectorProperties`

  ```java
  @ConfigurationProperties(prefix = "md.collector.binance")
  public class BinanceCollectorProperties {
      private Map<String, String> symbols;       // binanceSymbol -> internalSymbol
      private int reconnectDelaySeconds = 5;
  }
  ```

  Example YAML:

  ```yaml
  md:
    collector:
      binance:
        symbols:
          btcusdt: BTC-USD
        reconnectDelaySeconds: 5
  ```

#### 2.3.2 Application

- `com.rc.md.collector.binance.BinanceCollectorApplication`

  ```java
  @SpringBootApplication
  @ConfigurationPropertiesScan
  public class BinanceCollectorApplication { ... }
  ```

#### 2.3.3 Service – WebSocket

- `com.rc.md.collector.binance.service.BinanceWebSocketService`

  Responsibilities:

  - On startup (`@PostConstruct`), read `symbols` map.
  - For each entry (e.g., `btcusdt -> BTC-USD`):
    - Build stream name: `btcusdt@bookTicker`
    - Submit a task to an `ExecutorService` to manage WebSocket connection loop.

  Internal details:

  - `HttpClient httpClient = HttpClient.newHttpClient();`
  - `ExecutorService executor = Executors.newCachedThreadPool();`
  - `connectLoop(String stream, String internalSymbol)`:
    - Construct URL: `wss://stream.binance.com:9443/ws/{stream}`
    - Create `WebSocket` with custom `WebSocketListener`.
    - Use `wait()` / `notifyAll()` on the WebSocket instance to block until error/close.
    - On error/close, sleep `reconnectDelaySeconds` and reconnect.

- Inner class `WebSocketListener implements WebSocket.Listener`:

  - Maintains buffer `StringBuilder messageBuffer`.
  - `onText`:
    - Append fragments until `last == true`.
    - When `last`, call `handleMessage(json)`.

  - `handleMessage(String json)`:
    - Parse with `ObjectMapper` into `JsonNode`.
    - Extract fields:

      ```java
      JsonNode bidNode = node.get("b"); // best bid
      JsonNode askNode = node.get("a"); // best ask
      JsonNode timeNode = node.get("T"); // event time in ms
      ```

    - Validate presence of bid/ask.
    - Compute timestamp:

      ```java
      long ts;
      if (timeNode != null && !timeNode.isNull()) {
          ts = timeNode.asLong() / 1000; // ms -> sec
      } else {
          ts = Instant.now().getEpochSecond(); // fallback
      }
      ```

    - Construct:

      ```java
      BidAskEvent event = new BidAskEvent(internalSymbol, bid, ask, ts);
      ```

    - Serialize using `ObjectMapper` → JSON string.

    - Create Kafka `ProducerRecord<String, String>`:

      - Topic: `md.bidask.normalized`
      - Key: `internalSymbol`
      - Value: event JSON

    - `kafkaTemplate.send(record);`

  - `onError` / `onClose`:
    - Log error/close reason.
    - `synchronized (webSocket) { webSocket.notifyAll(); }` to unblock `connectLoop`.

#### 2.3.4 Config & Logging

- `application.yml`:

  - Kafka producer configs
  - Tracing/OTLP endpoint
  - Collector properties

- `logback-spring.xml`:

  - Console + file appender
  - Pattern includes `traceId` and `spanId`.

---

### 2.4 `md-collector-simulator`

**Purpose:** Produce synthetic `BidAskEvent` for dev/testing.

**Key types:**

- `SimulatorProducer` (under `com.rc.md.collector.simulator.service`):

  - Uses `@Slf4j`.
  - Injects `KafkaTemplate<String, String>`.
  - Periodically generates random-walk mid price around a base.
  - Creates bid/ask as `mid ± spread/2`.
  - Uses `Instant.now().getEpochSecond()` for timestamp.
  - Sends JSON `BidAskEvent` to `md.bidask.normalized`.

- Simulator-level configs:

  - Number of symbols
  - Tick interval
  - Jitter factors  
    (Details may be tuned as you extend the simulator.)

---

### 2.5 `md-aggregator`

**Purpose:** Consume normalized events, aggregate into OHLCV candles per (symbol, interval, bucket), store in TimescaleDB.

**Key packages & types:**

#### 2.5.1 Kafka Listener

- `com.rc.md.aggregator.kafka.BidAskListener`

  - Uses `@Slf4j`.
  - Annotated `@KafkaListener` on `md.bidask.normalized`.
  - Method (example signature):

    ```java
    @KafkaListener(topics = "md.bidask.normalized", groupId = "md-aggregator")
    public void onMessage(String message) { ... }
    ```

  - Steps:
    1. Deserialize `message` into `BidAskEvent` using `ObjectMapper`.
    2. For each configured interval (`IntervalDefinition`):
       - Compute bucket start time.
       - Delegate to `CandleService` / `CandleAggregator` (depending on final structure).

#### 2.5.2 Interval Configuration

- `com.rc.md.aggregator.config.IntervalConfig`

  - Similar to analytics side; holds `List<IntervalDefinition>`.
  - Methods:
    - `List<IntervalDefinition> getIntervals()`
    - `IntervalDefinition findById(String id)` (optional extension)
    - For aggregator, more commonly iterate over all intervals.

#### 2.5.3 Candle Entity & Repository

- `com.rc.md.aggregator.candle.CandleEntity`

  Likely structure:

  ```java
  @Entity
  @Table(name = "candles")
  public class CandleEntity {

      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;

      private String symbol;

      @Column(name = "interval_sec")
      private int intervalSec;

      @Column(name = "bucket_start")
      private OffsetDateTime bucketStart;

      private double open;
      private double high;
      private double low;
      private double close;

      private long volume;
  }
  ```

- `com.rc.md.aggregator.candle.CandleRepository`

  ```java
  public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

      Optional<CandleEntity> findBySymbolAndIntervalSecAndBucketStart(
          String symbol, int intervalSec, OffsetDateTime bucketStart
      );
  }
  ```

#### 2.5.4 Aggregation Logic

- `CandleService` or inline in `BidAskListener` (depending on your code evolution):

  - Compute mid price:

    ```java
    double mid = (event.bid() + event.ask()) / 2.0;
    ```

  - Compute bucket:

    ```java
    long bucketSec = (event.timestamp() / intervalSec) * intervalSec;
    OffsetDateTime bucketStart = OffsetDateTime.ofInstant(
        Instant.ofEpochSecond(bucketSec),
        ZoneOffset.UTC
    );
    ```

  - Load or create candle:

    ```java
    Optional<CandleEntity> opt = repo.findBySymbolAndIntervalSecAndBucketStart(
        symbol, intervalSec, bucketStart);

    CandleEntity candle = opt.orElseGet(() -> {
        CandleEntity c = new CandleEntity();
        c.setSymbol(symbol);
        c.setIntervalSec(intervalSec);
        c.setBucketStart(bucketStart);
        c.setOpen(mid);
        c.setHigh(mid);
        c.setLow(mid);
        c.setClose(mid);
        c.setVolume(0L);
        return c;
    });

    candle.setHigh(Math.max(candle.getHigh(), mid));
    candle.setLow(Math.min(candle.getLow(), mid));
    candle.setClose(mid);
    candle.setVolume(candle.getVolume() + 1);
    ```

  - Save back:

    ```java
    repo.save(candle);
    ```

**Concurrency consideration:**  
For v1, we rely on DB-level correctness and single-threaded processing per partition. Future versions could add optimistic locking / versioning on candle rows if needed.

---

### 2.6 `md-analytics`

**Purpose:** Serve OHLCV history via REST.

**Key packages & types:**

#### 2.6.1 Interval Config

- `com.rc.md.analytics.config.IntervalConfig`

  ```java
  public class IntervalConfig {

      private List<IntervalDefinition> intervals;

      public IntervalDefinition findById(String id) { ... }
  }
  ```

  Configured via YAML:

  ```yaml
  md:
    analytics:
      intervals:
        - id: 1m
          seconds: 60
        - id: 5m
          seconds: 300
  ```

#### 2.6.2 Candle Entity & Repository

- `com.rc.md.analytics.candle.CandleEntity` and `CandleRepository` mirror aggregator’s schema, but in analytics module (read-only side).

  ```java
  List<CandleEntity> findBySymbolAndIntervalSecAndBucketStartBetweenOrderByBucketStartAsc(
      String symbol, int intervalSec, OffsetDateTime from, OffsetDateTime to
  );
  ```

#### 2.6.3 History Controller

- `com.rc.md.analytics.api.HistoryController`

  - Mapping:

    ```java
    @GetMapping("/history")
    public HistoryResponse getHistory(
        @RequestParam String symbol,
        @RequestParam String interval,
        @RequestParam long from,
        @RequestParam long to
    ) { ... }
    ```

  - Flow:
    1. Validate `from <= to`; else return `HistoryResponse` with `s="error"`.
    2. Look up `IntervalDefinition` by `interval`. If not found → `s="error"`.
    3. Convert `from` / `to` (UNIX seconds) to `OffsetDateTime`.
    4. Query repository.
    5. Convert entity list to `HistoryResponse` arrays:
       - `t[i] = bucketStart.toEpochSecond(ZoneOffset.UTC)`
       - `o[i], h[i], l[i], c[i], v[i]` from entity.
    6. Set `s="ok"`.

---

### 2.7 `md-analytics-client`

**Purpose:** Provide a reusable Feign client for `/history` to other services (e.g., `md-load-test`).

**Key type:**

- `com.rc.md.client.analytics.AnalyticsClient`

  ```java
  @FeignClient(name = "md-analytics", url = "${md.analytics.base-url}")
  public interface AnalyticsClient {

      @GetMapping("/history")
      HistoryResponse getHistory(
          @RequestParam("symbol") String symbol,
          @RequestParam("interval") String interval,
          @RequestParam("from") long from,
          @RequestParam("to") long to
      );
  }
  ```

Configuration example:

```yaml
md:
  analytics:
    base-url: http://localhost:8080
```

---

### 2.8 `md-load-test`

**Purpose:** A load generator and performance measurement tool for `/history`.

**Key classes:**

- `com.rc.md.loadtest.LoadTestApplication`
  - `@SpringBootApplication`
  - `@EnableFeignClients(basePackages = "com.rc.md.client.analytics")`
  - Injects `LoadTestRunner` and runs it once as `CommandLineRunner`, then `System.exit(0)`.

- `com.rc.md.loadtest.LoadTestProperties`

  ```java
  @ConfigurationProperties(prefix = "md.load")
  public class LoadTestProperties {
      private int threads = 10;
      private int requestsPerThread = 100;
      private String symbol = "BTC-USD";
      private String interval = "1m";
      private long from = 1_700_000_000L;
      private long to = 1_700_000_600L;
      private boolean verboseErrors = false;
  }
  ```

- `com.rc.md.loadtest.LoadTestRunner`

  - Injects `AnalyticsClient` + `LoadTestProperties`.
  - Executes:

    ```java
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch latch = new CountDownLatch(threads);
    List<Double> latenciesMillis = Collections.synchronizedList(new ArrayList<>());
    List<String> errors = Collections.synchronizedList(new ArrayList<>());
    ```

  - Each worker:
    - Performs `requestsPerThread` REST calls.
    - Measures latency via `System.nanoTime()`.
    - Adds latency to `latenciesMillis`.
    - Checks `HistoryResponse.s` and appends errors.
  - After completion:
    - Calculates statistics over `latenciesMillis`.
    - Logs min/avg/max/p50/p95/p99 and throughput.

---

## 3. Kafka Topic Design

### 3.1 Topic: `md.bidask.normalized`

- **Purpose:** Single, normalized source of truth for real-time bid/ask events.
- **Key:** `symbol` (e.g., `BTC-USD`).
- **Value:** JSON string representing `BidAskEvent`.
- **Producer(s):**
  - `md-collector-binance`
  - `md-collector-simulator`
- **Consumer(s):**
  - `md-aggregator`
  - (Future) additional processors (e.g., signals, order book builders).

---

## 4. TimescaleDB Schema

Core table `candles` (logical structure):

```sql
CREATE TABLE candles (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    interval_sec INT NOT NULL,
    bucket_start TIMESTAMPTZ NOT NULL,
    open DOUBLE PRECISION NOT NULL,
    high DOUBLE PRECISION NOT NULL,
    low DOUBLE PRECISION NOT NULL,
    close DOUBLE PRECISION NOT NULL,
    volume BIGINT NOT NULL
);
```

Suggested indexes:

```sql
CREATE INDEX idx_candles_symbol_interval_bucket
    ON candles (symbol, interval_sec, bucket_start);
```

TimescaleDB extension:

- `SELECT create_hypertable('candles', 'bucket_start');`

(You can apply this manually or via Flyway/Liquibase as you evolve the infra.)

---

## 5. Tracing & Logging (Implementation-Level)

### 5.1 Tracing

- Dependencies:
  - `micrometer-tracing-bridge-otel`
  - `opentelemetry-exporter-otlp`
- Config (per service):

  ```yaml
  management:
    tracing:
      sampling:
        probability: 1.0
    otlp:
      tracing:
        endpoint: http://localhost:4317
  ```

- Traces:
  - HTTP server spans (`md-analytics`)
  - Kafka spans (producer/consumer) via Spring/Kafka integration (depending on auto config).
  - `md-load-test` also sends traces, so you can see client-side spans.

### 5.2 Logging

- `logback-spring.xml` in each module:

  - Console appender
  - Rolling file appender (module-specific names)
  - Pattern:

    ```text
    %d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] traceId=%X{traceId} spanId=%X{spanId} %logger{36} - %msg%n
    ```

- Logging API:
  - Lombok `@Slf4j` used throughout:
    - `log.info("...")`
    - `log.warn("...")`
    - `log.error("...")`

---

## 6. Testing Structure (Code-Level)

### 6.1 Aggregator

- `AggregatorIntegrationTest`:

  - Starts:
    - H2 DB
    - Embedded Kafka broker
  - Wires full Spring context for `md-aggregator`.
  - Produces a `BidAskEvent` message into Kafka.
  - Waits until a `CandleEntity` appears in DB.
  - Asserts:
    - Candle created with expected open/close and volume.

- `BucketLogicUnitTest`:

  - Pure unit test verifying bucket calculation logic for various intervals and timestamps.

### 6.2 Analytics

- `HistoryControllerIntegrationTest` (H2 DB + MockMvc):

  - Inserts `CandleEntity` into DB.
  - Calls `/api/v1/history` via MockMvc.
  - Verifies JSON payload.

- `IntervalConfigTest`:

  - Unit test verifying `findById` logic.

- `HistoryControllerValidationTest` (standalone MockMvc):

  - No Spring context.
  - Uses mocked `CandleRepository` + real `IntervalConfig`.
  - Tests error paths:
    - `from > to`
    - Unsupported interval.

### 6.3 Load Test

- `md-load-test` uses the **actual Feign client** against a running `md-analytics` instance:
  - This is more of a **tool** than a JUnit test.
  - Allows manual performance experiments.

---