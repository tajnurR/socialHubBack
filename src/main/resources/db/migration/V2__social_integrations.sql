-- =============================================================================
-- V2: Social platform integrations (connected accounts + encrypted credentials).
--
-- Supersedes the V1 `social_accounts` scaffold with `social_integrations`, which
-- stores the per-organization connection to a platform plus its (encrypted)
-- access token. The `posts` FK is repointed accordingly.
-- =============================================================================

create table social_integrations (
    id                  bigint generated always as identity primary key,
    organization_id     bigint       not null references organizations (id),
    platform            varchar(30)  not null,
    external_account_id varchar(255) not null,        -- e.g. Facebook Page ID
    access_token        text         not null,        -- encrypted at rest (AES-GCM)
    display_name        varchar(255),
    status              varchar(30)  not null default 'CONNECTED',
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    created_by          varchar(150),
    constraint uq_social_integrations unique (organization_id, platform, external_account_id)
);

create index idx_social_integrations_org on social_integrations (organization_id);

-- Repoint posts from the removed social_accounts scaffold to social_integrations.
-- Dropping the column also drops its FK constraint and idx_posts_account.
alter table posts drop column social_account_id;
alter table posts add column social_integration_id bigint references social_integrations (id);
create index idx_posts_integration on posts (social_integration_id);

drop table social_accounts;

-- Seed a default organization for local/dev use until SSO + org provisioning exist.
-- (TODO[SSO]: organizations will be created from the identity provider / onboarding.)
insert into organizations (id, name, slug, status, created_by)
    overriding system value
    values (1, 'Default Organization', 'default', 'ACTIVE', 'system');

-- Keep the identity sequence clear of the seeded id.
alter table organizations alter column id restart with 100;
