-- =============================================================================
-- V1: Foundational multi-tenant schema.
--
-- Tenancy model: a row's owning tenant is its `organization_id`. Every
-- tenant-scoped table carries that column so data can be isolated per org.
-- Audit columns (created_at, updated_at, created_by) are populated by JPA
-- auditing (see BaseEntity / JpaAuditingConfig) and defaulted at the DB level.
-- =============================================================================

create table organizations (
    id          bigint generated always as identity primary key,
    name        varchar(150) not null,
    slug        varchar(150) not null unique,
    status      varchar(30)  not null default 'ACTIVE',
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    created_by  varchar(150)
);

create table users (
    id               bigint generated always as identity primary key,
    organization_id  bigint       not null references organizations (id),
    email            varchar(255) not null,
    display_name     varchar(150),
    role             varchar(30)  not null default 'MEMBER',
    -- external_subject maps to the SSO/OIDC `sub` claim once SSO is wired (TODO[SSO]).
    external_subject varchar(255),
    status           varchar(30)  not null default 'ACTIVE',
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    created_by       varchar(150),
    constraint uq_users_org_email unique (organization_id, email)
);

create table social_accounts (
    id                  bigint generated always as identity primary key,
    organization_id     bigint       not null references organizations (id),
    platform            varchar(30)  not null,
    external_account_id varchar(255) not null,
    display_name        varchar(255),
    status              varchar(30)  not null default 'PENDING',
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    created_by          varchar(150),
    constraint uq_social_accounts unique (organization_id, platform, external_account_id)
);

create table posts (
    id                bigint generated always as identity primary key,
    organization_id   bigint       not null references organizations (id),
    social_account_id bigint       references social_accounts (id),
    platform          varchar(30)  not null,
    external_post_id  varchar(255),
    content           text,
    status            varchar(30)  not null default 'DRAFT',
    published_at      timestamptz,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    created_by        varchar(150)
);

create index idx_users_org on users (organization_id);
create index idx_social_accounts_org on social_accounts (organization_id);
create index idx_posts_org on posts (organization_id);
create index idx_posts_account on posts (social_account_id);
