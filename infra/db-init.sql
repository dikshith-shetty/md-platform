-- =========================================
-- Enable TimescaleDB extension (if needed)
-- =========================================
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- =========================================
-- Create candles table as hypertable
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

-- Useful index for history queries
CREATE INDEX IF NOT EXISTS idx_candles_symbol_interval_time
    ON candles (symbol, interval_sec, bucket_start DESC);

-- ========================================
-- Insert sample candles for:
-- BTC-USD, ETH-USD
-- 1m (60s), 5m (300s)
-- Starting epoch: 1764470000
-- 10,000 rows per symbol, per interval
-- ========================================

WITH
base AS (
    SELECT 1764470000::bigint AS base_ts
),
symbols AS (
    SELECT unnest(ARRAY['BTC-USD', 'ETH-USD']) AS symbol
),
intervals AS (
    SELECT unnest(ARRAY[60, 300]) AS interval_sec      -- 1m, 5m
),
grid AS (
    -- 10,000 rows per symbol x interval
    SELECT
        s.symbol,
        i.interval_sec,
        generate_series(0, 9999) AS n
    FROM symbols s
    CROSS JOIN intervals i
),
generated AS (
    SELECT
        g.symbol,
        g.interval_sec,
        (b.base_ts + g.n * g.interval_sec) AS epoch_ts,

        -- Base price per symbol
        CASE g.symbol
            WHEN 'BTC-USD' THEN 90000
            WHEN 'ETH-USD' THEN 3000
            ELSE 1000
        END
        + ((random() - 0.5) * 200) AS mid_price_raw,

        random() AS r1,
        random() AS r2,
        random() AS r3,
        random() AS r4
    FROM grid g
    CROSS JOIN base b
),
candles_data AS (
    SELECT
        symbol,
        interval_sec,
        to_timestamp(epoch_ts) AT TIME ZONE 'UTC' AS bucket_start,
        -- Generate OHLC values
        (mid_price_raw + (r1 - 0.5) * 10)::double precision AS open,
        (mid_price_raw + abs(r2) * 15)::double precision    AS high,
        (mid_price_raw - abs(r3) * 15)::double precision    AS low,
        (mid_price_raw + (r4 - 0.5) * 10)::double precision AS close,

        (10 + floor(random() * 500))::bigint AS volume
    FROM generated
)
INSERT INTO candles (symbol, interval_sec, bucket_start, open, high, low, close, volume)
SELECT
    symbol,
    interval_sec,
    bucket_start,
    open,
    GREATEST(open, high, close, low) AS high,
    LEAST(open, high, close, low)    AS low,
    close,
    volume
FROM candles_data
ORDER BY symbol, interval_sec, bucket_start
ON CONFLICT (symbol, interval_sec, bucket_start) DO NOTHING;

-- ========================================
-- 4. Sample data Validation
-- ========================================

SELECT symbol, interval_sec, count(*) AS rows
FROM candles
WHERE symbol IN ('BTC-USD', 'ETH-USD')
  AND interval_sec IN (60, 300)
GROUP BY symbol, interval_sec
ORDER BY symbol, interval_sec;

SELECT *
FROM candles
WHERE symbol = 'BTC-USD'
  AND interval_sec = 60
ORDER BY bucket_start
LIMIT 20;
