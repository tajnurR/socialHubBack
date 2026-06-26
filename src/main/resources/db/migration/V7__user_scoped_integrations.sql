-- =============================================================================
-- V7: Make Facebook page connections user-owned (fix shared-config isolation bug).
--
-- social_integrations was scoped to organization_id only, so users sharing an org
-- could see/act on each other's connected pages. Add user_id ownership and scope
-- all queries by (organization_id, user_id). Backfill from the owning app config
-- (falling back to the seeded user) so existing rows get an owner.
-- =============================================================================

alter table social_integrations add column user_id bigint references users (id);

-- Backfill: owner = the user who owns the linked app config; else the seeded user (1).
update social_integrations si
   set user_id = coalesce(
       (select fac.user_id from facebook_app_credentials fac where fac.id = si.app_credential_id),
       1);

alter table social_integrations alter column user_id set not null;

-- Uniqueness is now per-user (a connection belongs to one user).
alter table social_integrations drop constraint uq_social_integrations;
alter table social_integrations
    add constraint uq_social_integrations
        unique (organization_id, user_id, platform, external_account_id);

create index idx_social_integrations_org_user on social_integrations (organization_id, user_id);
