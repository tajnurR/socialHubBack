-- =============================================================================
-- V4: Per-organization Facebook app credentials.
--
-- Each org provides its OWN Meta app (App ID + App Secret). The secret is stored
-- encrypted at rest. The OAuth token exchange uses these per-org credentials
-- instead of a single server-wide app (see FacebookAppCredentialProvider — the
-- abstraction that lets us switch back to a shared app later).
-- =============================================================================

create table facebook_app_credentials (
    id              bigint generated always as identity primary key,
    organization_id bigint       not null references organizations (id),
    app_id          varchar(64)  not null,
    app_secret      text         not null,        -- encrypted at rest (AES-GCM)
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    created_by      varchar(150),
    constraint uq_fb_app_credentials_org unique (organization_id)
);
