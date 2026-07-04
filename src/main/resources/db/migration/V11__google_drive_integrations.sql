-- =============================================================================
-- V11: User-owned Google Drive storage integrations.
--
-- Stores one Google Drive connection per (organization_id, user_id). OAuth tokens
-- are encrypted by the application before persistence and are never returned by
-- the API.
-- =============================================================================

create table google_drive_integrations (
    id                       bigint generated always as identity primary key,
    organization_id          bigint       not null references organizations (id),
    user_id                  bigint       not null references users (id),
    google_account_id        varchar(255),
    google_account_name      varchar(255),
    google_account_email     varchar(255),
    status                   varchar(30)  not null default 'DISCONNECTED',
    access_token             text,
    refresh_token            text,
    token_type               varchar(40),
    scopes                   text,
    token_obtained_at        timestamptz,
    access_token_expires_at  timestamptz,
    connected_at             timestamptz,
    last_sync_at             timestamptz,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),
    created_by               varchar(150),
    constraint uq_google_drive_integrations_owner unique (organization_id, user_id)
);

create index idx_google_drive_integrations_org_user
    on google_drive_integrations (organization_id, user_id);
