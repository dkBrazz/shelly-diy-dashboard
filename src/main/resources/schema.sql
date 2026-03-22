-- Initial Schema for TimescaleDB

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
    
    a_voltage   DOUBLE PRECISION,
    a_current   DOUBLE PRECISION,
    a_power     DOUBLE PRECISION,
    
    b_voltage   DOUBLE PRECISION,
    b_current   DOUBLE PRECISION,
    b_power     DOUBLE PRECISION,
    
    c_voltage   DOUBLE PRECISION,
    c_current   DOUBLE PRECISION,
    c_power     DOUBLE PRECISION,
    
    total_power DOUBLE PRECISION,
    temperature DOUBLE PRECISION,
    
    PRIMARY KEY (time, device_id)
);

-- This will only work if TimescaleDB extension is active
SELECT create_hypertable('power_measures', 'time', if_not_exists => TRUE);
