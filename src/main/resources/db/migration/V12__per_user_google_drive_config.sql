-- =============================================================================
-- V12: Per-user Google Drive OAuth app credentials.
--
-- Matches the Facebook credential model: each user can save one or more Google
-- OAuth client id/secret configs. Client secrets are encrypted by the app before
-- persistence and never returned to the frontend.
-- =============================================================================

create table google_drive_app_credentials (
    id               bigint generated always as identity primary key,
    organization_id  bigint       not null references organizations (id),
    user_id          bigint       not null references users (id),
    label            varchar(120),
    client_id        varchar(255) not null,
    client_secret    text         not null,
    redirect_uri     varchar(500),
    scopes           text,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    created_by       varchar(150)
);

alter table google_drive_integrations
    add column app_credential_id bigint references google_drive_app_credentials (id);

create index idx_google_drive_app_credentials_org_user
    on google_drive_app_credentials (organization_id, user_id);
