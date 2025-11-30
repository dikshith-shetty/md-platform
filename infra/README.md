# Infra for md-platform

This folder contains Docker Compose for local development:

- **Zookeeper**: `md-zookeeper` (port 2181)
- **Kafka**: `md-kafka` (broker on localhost:9092)
- **Kafka UI**: `md-kafka-ui` (http://localhost:8085)
- **TimescaleDB**: `md-timescaledb` (Postgres-compatible, localhost:5432)
- **OpenTelemetry Collector**: `md-otel-collector` (OTLP gRPC :4317, HTTP :4318)
- **Jaeger**: `md-jaeger` (UI at http://localhost:16686)

## How to start infra

From the `infra` directory:

```bash
docker compose up -d
```

Then configure your Spring Boot apps with:

- Kafka bootstrap servers: `localhost:9092`
- Postgres URL: `jdbc:postgresql://localhost:5432/md_db`
  - username: `md_user`
  - password: `md_password`
- OTLP endpoint for traces (when you add tracing):
  - `http://localhost:4317` or `http://otel-collector:4317` inside Docker network

Jaeger UI will be available at: http://localhost:16686
Kafka UI will be available at: http://localhost:8085
