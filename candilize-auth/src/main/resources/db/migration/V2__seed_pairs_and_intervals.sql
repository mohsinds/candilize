-- Seed supported pairs: BTCUSDT, ETHUSDT, SOLUSDT, XRPUSDT, ADAUSDT
INSERT INTO supported_pairs (symbol, base_asset, quote_asset, enabled) VALUES
('BTCUSDT', 'BTC', 'USDT', TRUE),
('ETHUSDT', 'ETH', 'USDT', TRUE),
('SOLUSDT', 'SOL', 'USDT', TRUE),
('XRPUSDT', 'XRP', 'USDT', TRUE),
('ADAUSDT', 'ADA', 'USDT', TRUE);

-- Seed supported intervals: 1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w, 1M
INSERT INTO supported_intervals (interval_code, description, enabled) VALUES
('1m', '1 minute', TRUE),
('5m', '5 minutes', TRUE),
('15m', '15 minutes', TRUE),
('30m', '30 minutes', TRUE),
('1h', '1 hour', TRUE),
('4h', '4 hours', TRUE),
('1d', '1 day', TRUE),
('1w', '1 week', TRUE),
('1M', '1 month', TRUE);
