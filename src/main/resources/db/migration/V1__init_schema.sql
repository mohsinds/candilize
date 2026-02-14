-- Users table for authentication
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('ROLE_USER', 'ROLE_ADMIN') NOT NULL DEFAULT 'ROLE_USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Supported trading pairs
CREATE TABLE supported_pairs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    base_asset VARCHAR(10) NOT NULL,
    quote_asset VARCHAR(10) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Supported candle intervals
CREATE TABLE supported_intervals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    interval_code VARCHAR(5) NOT NULL UNIQUE,
    description VARCHAR(50),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- Candle OHLCV data
CREATE TABLE candle_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    interval_code VARCHAR(5) NOT NULL,
    open_time BIGINT NOT NULL,
    open_price DECIMAL(24,8) NOT NULL,
    high_price DECIMAL(24,8) NOT NULL,
    low_price DECIMAL(24,8) NOT NULL,
    close_price DECIMAL(24,8) NOT NULL,
    volume DECIMAL(24,8) NOT NULL,
    close_time BIGINT NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    UNIQUE KEY uq_candle (symbol, interval_code, open_time, exchange)
);

CREATE INDEX idx_candle_lookup ON candle_data(symbol, interval_code, open_time, exchange);
