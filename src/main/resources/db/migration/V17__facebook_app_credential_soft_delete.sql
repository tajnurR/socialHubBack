-- =============================================================================
-- V17: Soft-delete lifecycle for per-user Facebook app credentials.
--
-- Credentials are not hard-deleted so prior audit/history remains intact. Active
-- lookup paths filter by status/deleted_at so removed app configs cannot be used
-- for future OAuth exchanges.
-- =============================================================================

alter table facebook_app_credentials
    add column status varchar(30) not null default 'ACTIVE',
    add column deleted_at timestamptz;

create index idx_fb_app_credentials_active
    on facebook_app_credentials (organization_id, user_id, status, deleted_at);
