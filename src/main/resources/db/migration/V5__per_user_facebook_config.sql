-- =============================================================================
-- V5: Per-user, multi-app Facebook configuration.
--
-- - Seeds the dev user that CurrentUserProvider returns until SSO exists.
-- - Facebook app credentials become per-user and may have MANY per user/org
--   (each carrying its own redirect/scopes/api-version); the one-per-org unique
--   constraint is dropped.
-- - Each connected page (social_integrations) links to the app config used to
--   connect it (one app config -> many connected pages).
-- =============================================================================

-- Dev user (CurrentUserProvider default). Org #1 was seeded in V2.
insert into users (id, organization_id, email, display_name, role, status, created_by)
    overriding system value
    values (1, 1, 'dev@socialhub.local', 'Dev User', 'OWNER', 'ACTIVE', 'system');
alter table users alter column id restart with 100;

-- Multiple app configs per user/org.
alter table facebook_app_credentials drop constraint uq_fb_app_credentials_org;
alter table facebook_app_credentials
    add column user_id      bigint references users (id),
    add column label        varchar(120),
    add column redirect_uri varchar(500),
    add column scopes       varchar(500),
    add column api_version  varchar(20);
update facebook_app_credentials set user_id = 1 where user_id is null;
create index idx_fb_app_credentials_org_user on facebook_app_credentials (organization_id, user_id);

-- A connected page belongs to the app config it was connected through.
alter table social_integrations
    add column app_credential_id bigint references facebook_app_credentials (id);
