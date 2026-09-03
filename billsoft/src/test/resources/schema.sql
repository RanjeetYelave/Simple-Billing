CREATE TABLE system_stats (
    id BIGINT PRIMARY KEY,
    lifetime_requests BIGINT,
    lifetime_uptime_seconds BIGINT,
    server_starts_count BIGINT,
    first_started_at BIGINT,
    last_started_at BIGINT,
    updated_at BIGINT
);
INSERT INTO system_stats (id, lifetime_requests, lifetime_uptime_seconds, server_starts_count, first_started_at, last_started_at, updated_at) VALUES (1, 0, 0, 0, 0, 0, 0);
