-- Recommended alternative schema for PostgreSQL 18.2 + TimescaleDB 2.25.2.
-- This is an alternative to schema.sql, not something to run alongside it
-- in the same database without dropping or renaming the existing objects.
--
-- Key differences from schema.sql:
-- 1. Fixed-point INTEGER storage for all measures.
--    All measure values are expected to be multiplied by 1000 before insert.
-- 2. Explicit Timescale hypertable configuration using the current CREATE TABLE
--    syntax with columnstore settings.
-- 3. A single PRIMARY KEY(device_id, time) index to match the dominant
--    query pattern and avoid extra default hypertable indexes.
-- 4. A readable view that scales values back to floating-point form.
-- 5. No manual add_columnstore_policy call is needed on TimescaleDB 2.25.2:
--    CREATE TABLE ... WITH (...) creates the columnstore policy automatically.

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS devices (
    id SERIAL PRIMARY KEY,
    external_id TEXT UNIQUE NOT NULL,
    type TEXT,
    code TEXT,
    name TEXT,
    gen TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS power_measures (
    time        TIMESTAMPTZ NOT NULL,
    device_id   INTEGER NOT NULL REFERENCES devices(id),

    -- Values are stored as raw_value * 1000 to preserve up to 3 fractional digits.
    a_voltage   INTEGER NOT NULL,
    a_current   INTEGER NOT NULL,
    a_power     INTEGER NOT NULL,

    b_voltage   INTEGER NOT NULL,
    b_current   INTEGER NOT NULL,
    b_power     INTEGER NOT NULL,

    c_voltage   INTEGER NOT NULL,
    c_current   INTEGER NOT NULL,
    c_power     INTEGER NOT NULL,

    total_power INTEGER NOT NULL,
    temperature INTEGER NOT NULL,

    PRIMARY KEY (device_id, time)
) WITH (
    tsdb.hypertable,
    tsdb.partition_column = 'time',
    tsdb.chunk_interval = '1 day',
    tsdb.create_default_indexes = false,
    tsdb.segmentby = 'device_id',
    tsdb.orderby = 'time DESC'
);

COMMENT ON TABLE power_measures IS
    'Recommended fixed-point hypertable. Measure columns store value * 1000.';

-- Convenience view for ad hoc reads and manual queries.
CREATE OR REPLACE VIEW power_measures_readable AS
SELECT
    time,
    device_id,
    a_voltage::DOUBLE PRECISION / 1000.0 AS a_voltage,
    a_current::DOUBLE PRECISION / 1000.0 AS a_current,
    a_power::DOUBLE PRECISION / 1000.0 AS a_power,
    b_voltage::DOUBLE PRECISION / 1000.0 AS b_voltage,
    b_current::DOUBLE PRECISION / 1000.0 AS b_current,
    b_power::DOUBLE PRECISION / 1000.0 AS b_power,
    c_voltage::DOUBLE PRECISION / 1000.0 AS c_voltage,
    c_current::DOUBLE PRECISION / 1000.0 AS c_current,
    c_power::DOUBLE PRECISION / 1000.0 AS c_power,
    total_power::DOUBLE PRECISION / 1000.0 AS total_power,
    temperature::DOUBLE PRECISION / 1000.0 AS temperature
FROM power_measures;

COMMENT ON VIEW power_measures_readable IS
    'Readable projection of power_measures with integer values scaled back by 1000.';

-- Note:
-- If later measurements prove that a column safely fits into SMALLINT,
-- narrow that column only after validating real production ranges.
