-- =============================================================================
-- V23: Per-user LinkedIn OAuth app credentials.
--
-- LinkedIn profile connections reuse social_integrations for encrypted member
-- tokens, but app client id/secret pairs stay in a separate user-owned table so
-- LinkedIn remains independent from Meta/Facebook app configuration.
-- =============================================================================

create table linkedin_app_credentials (
    id               bigint generated always as identity primary key,
    organization_id  bigint       not null references organizations (id),
    user_id          bigint       not null references users (id),
    label            varchar(120),
    client_id        varchar(255) not null,
    client_secret    text         not null,
    redirect_uri     varchar(500),
    scopes           text,
    api_version      varchar(20),
    status           varchar(30)  not null default 'ACTIVE',
    deleted_at       timestamptz,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    created_by       varchar(150)
);

create index idx_linkedin_app_credentials_org_user
    on linkedin_app_credentials (organization_id, user_id, status, deleted_at);
