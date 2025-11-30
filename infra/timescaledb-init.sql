-- =========================================
-- 1. Enable TimescaleDB extension (if needed)
-- =========================================
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- =========================================
-- 2. Create candles table as hypertable
-- =========================================
CREATE TABLE IF NOT EXISTS candles (
    symbol        TEXT        NOT NULL,
    interval_sec  INTEGER     NOT NULL,
    bucket_start  TIMESTAMPTZ NOT NULL,
    open          DOUBLE PRECISION NOT NULL,
    high          DOUBLE PRECISION NOT NULL,
    low           DOUBLE PRECISION NOT NULL,
    close         DOUBLE PRECISION NOT NULL,
    volume        BIGINT      NOT NULL,
    CONSTRAINT candles_pkey PRIMARY KEY (symbol, interval_sec, bucket_start)
);

SELECT create_hypertable('candles', 'bucket_start', if_not_exists => TRUE);

-- Useful index for typical /history queries
CREATE INDEX IF NOT EXISTS idx_candles_symbol_interval_time
    ON candles (symbol, interval_sec, bucket_start DESC);

