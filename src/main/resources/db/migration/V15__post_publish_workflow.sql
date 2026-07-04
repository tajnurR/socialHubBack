-- =============================================================================
-- V15: Native scheduled publishing workflow metadata.
--
-- Adds response/retry fields for the safer draft -> pending -> processing ->
-- published/failed lifecycle. Existing rows default to "not retried yet".
-- =============================================================================

alter table posts
    add column publish_response_summary varchar(1000),
    add column last_retry_at            timestamptz;
