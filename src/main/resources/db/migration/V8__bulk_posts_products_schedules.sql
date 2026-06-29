-- =============================================================================
-- V8: Bulk posting, products, and scheduling.
--
-- Evolves `posts` into a draft → scheduled → posted lifecycle (owned per user),
-- adds a demo `products` table and `schedule_events`. The posts table is empty,
-- so columns can be added NOT NULL directly.
-- =============================================================================

-- posts: owner + content/link/media + scheduling + result fields.
alter table posts
    add column user_id           bigint not null references users (id),
    add column link              varchar(2000),
    add column media_url         varchar(2000),
    add column product_id        bigint,
    add column scheduled_at      timestamptz,
    add column error_message     varchar(1000),
    add column retry_count       integer not null default 0,
    add column schedule_event_id bigint;

create index idx_posts_org_user on posts (organization_id, user_id);
-- Drives the scheduler's "due posts" claim query.
create index idx_posts_status_scheduled on posts (status, scheduled_at);

-- Demo product catalog (a post can reference the product it is about).
create table products (
    id              bigint generated always as identity primary key,
    organization_id bigint       not null references organizations (id),
    user_id         bigint       not null references users (id),
    name            varchar(200) not null,
    sku             varchar(100),
    description     text,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    created_by      varchar(150)
);
create index idx_products_org_user on products (organization_id, user_id);

-- A schedule event groups posts and applies a mode (EXPLICIT or INTERVAL).
create table schedule_events (
    id              bigint generated always as identity primary key,
    organization_id bigint       not null references organizations (id),
    user_id         bigint       not null references users (id),
    name            varchar(200) not null,
    mode            varchar(20)  not null,         -- EXPLICIT | INTERVAL
    start_time      timestamptz,
    interval_hours  integer,
    status          varchar(20)  not null default 'ACTIVE',
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    created_by      varchar(150)
);
create index idx_schedule_events_org_user on schedule_events (organization_id, user_id);

-- Post foreign keys (added after the referenced tables exist).
alter table posts add constraint fk_posts_product
    foreign key (product_id) references products (id);
alter table posts add constraint fk_posts_schedule_event
    foreign key (schedule_event_id) references schedule_events (id);

-- Seed demo products for the seeded users (dev=1, admin=100).
insert into products (organization_id, user_id, name, sku, description, created_by) values
    (1, 1,   'Starter Plan', 'SKU-START',  'Entry-level subscription',  'system'),
    (1, 1,   'Pro Plan',     'SKU-PRO',    'Professional subscription', 'system'),
    (1, 100, 'Demo Widget',  'SKU-WIDGET', 'A sample physical product', 'system');
