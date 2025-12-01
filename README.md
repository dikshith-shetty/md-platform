# MD Platform – Market Data Processing System

This repository contains a modular **market data pipeline** built using **Java 17+, Spring Boot 3**, designed for real‑time bid/ask ingestion, candle aggregation, analytics queries, load testing, and distributed tracing support.

---

# 📦 Modules Overview

| Module | Description |
|--------|-------------|
| **md-collector-binance** | Connects to Binance WebSocket @bookTicker stream, normalizes bid/ask events using exchange timestamps, publishes to Kafka. |
| **md-collector-simulator** | Generates synthetic bid/ask events for development and load testing. |
| **md-aggregator** | Consumes normalized events, buckets them into candles (configurable intervals), stores in TimescaleDB. |
| **md-analytics** | Exposes REST API to fetch OHLCV candle data. |
| **md-analytics-client** | Feign client jar to integrate analytics APIs in other services. |
| **md-load-test** | CLI tool to load-test md-analytics API for latency & throughput. |
| **config-server** | Spring Cloud Config Server for centralized configuration. |
| **md-common** | Shared models, configs, DTOs. |

---

#  🐳 How to Run the Entire System (Local Deployment)

## 📋 System Requirements

### Minimum Hardware
 - CPU: 2+ cores (4+ recommended)
 - RAM: 8GB minimum (16GB recommended)
 - Disk: 20GB free space
 - OS: Windows 10/11, macOS 10.14+, or Linux (Ubuntu 20.04+/CentOS 8+)

### Software Prerequisites
  - Docker Desktop 24.0+ ([Download](https://www.docker.com/products/docker-desktop/))
  - Git 2.30+ ([Download](https://git-scm.com/downloads))
  - JDK 17+ ([Download](https://www.oracle.com/java/technologies/javase/jdk17-0-13-later-archive-downloads.html))
  - Maven 3.9+ ([Download](https://maven.apache.org/download.cgi))

## 🛠️ Environment Verification

  1. Check Docker is running
  ```sh 
    docker --version
  ```
  2. Check Docker Compose is working
  ```sh
    docker-compose --version
  ```
  3. Check Git
  ```sh 
    git --version
  ```
  4. Check JDK
  ```sh 
    javac -version
  ```
  5. Check Maven
  ```sh 
    mvn -version
  ```

## 🏗️ Deploy and Run


#### 1. Clone GitHub Repository
```sh 
  git clone https://github.com/dikshith-shetty/md-platform.git
```

#### 2. Change working directory to `md-platform`
```sh 
 cd md-platform
```

#### 3. Run deploy script 
*** For 🐧 Linux/macOS: Run `deploy.sh`***
```sh 
  # make deploy script executable
  chmod +x deploy.sh
  
  # Check script permissions
  ls -la deploy.sh
  
  # Run deploy script
  ./deploy.sh  
```
*** For 🪟 Windows: Run `deploy.bat`

```sh 
  deploy.cmd
```

## 🌐 Access the Application

#### Access URLs
- Main App swagger ui: http://localhost:8080/swagger-ui/index.html
- Kafka UI: **http://localhost:8085**
- Jaeger UI: **http://localhost:16686**

### Query Candle History
```sh
  curl "http://localhost:8080/api/v1/history?symbol=BTC-USD&interval=1m&from=1764470000&to=1764470200"
```
***replace `from` and `to` values with the latest timestamp in seconds.*** 

---

## Run Load Test (Optional)

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
- **Integration tests 

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


