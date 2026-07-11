create table chatgpt_credentials (
    id              bigserial primary key,
    organization_id bigint       not null references organizations (id),
    user_id         bigint       not null references users (id),
    label           varchar(120),
    api_key         text         not null,
    model           varchar(80)  not null,
    status          varchar(30)  not null default 'CONNECTED',
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    created_by      varchar(150),
    unique (organization_id, user_id)
);

create index idx_chatgpt_credentials_org_user
    on chatgpt_credentials (organization_id, user_id);
