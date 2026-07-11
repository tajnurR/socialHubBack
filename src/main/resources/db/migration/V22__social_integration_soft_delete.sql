-- Social integrations are soft-deleted so connected account history can remain
-- available for audit/reference while active app flows hide removed accounts.
alter table social_integrations
    add column deleted_at timestamptz;

create index idx_social_integrations_active
    on social_integrations (organization_id, user_id, platform, deleted_at);
