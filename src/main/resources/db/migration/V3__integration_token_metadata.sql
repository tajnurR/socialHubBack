-- =============================================================================
-- V3: Token lifecycle metadata for integrations (OAuth flow).
--
-- Adds how/when the stored token was obtained and when it expires (null = the
-- non-expiring Page token derived from a long-lived user token). The `status`
-- column already exists; it now also takes the value 'REAUTH_REQUIRED' when an
-- API call detects an invalidated/expired token.
-- =============================================================================

alter table social_integrations
    add column token_type        varchar(40),
    add column token_obtained_at timestamptz,
    add column token_expires_at  timestamptz;
