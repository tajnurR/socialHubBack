-- =============================================================================
-- V9: Rich schedules + persisted schedule templates.
--
-- Reuses the existing `schedule_events` table as the Schedule aggregate and the
-- existing `posts` table for linked schedule posts. No automatic publishing
-- worker is added here; this migration only persists planning data.
-- =============================================================================

alter table schedule_events
    add column description           text,
    add column color                 varchar(20),
    add column platforms             varchar(500),
    add column schedule_type         varchar(30),
    add column days_of_week          varchar(100),
    add column posting_time          time,
    add column timezone              varchar(100),
    add column start_date            date,
    add column end_date              date,
    add column daily_post_limit      integer,
    add column notify_success        boolean not null default true,
    add column notify_failure        boolean not null default true,
    add column notify_next_reminder  boolean not null default true;

update schedule_events
   set schedule_type = case when mode = 'INTERVAL' then 'custom' else 'one-time' end,
       color = coalesce(color, '#4f46e5'),
       timezone = coalesce(timezone, 'Asia/Dhaka'),
       posting_time = coalesce(posting_time, cast('20:00' as time)),
       start_date = coalesce(start_date, cast(created_at as date)),
       platforms = coalesce(platforms, 'FACEBOOK'),
       status = lower(status);

-- Schedule planning can include posts for platforms/accounts that are not
-- connected yet; publishing still requires a connected integration at publish time.
alter table posts alter column social_integration_id drop not null;

alter table posts
    add column title           varchar(255),
    add column hashtags        varchar(500),
    add column cta             varchar(255),
    add column time_override   time,
    add column sort_order      integer not null default 0;

create index idx_posts_schedule_sort on posts (schedule_event_id, sort_order);

create table schedule_templates (
    id                    bigint generated always as identity primary key,
    organization_id       bigint       not null references organizations (id),
    user_id               bigint       not null references users (id),
    name                  varchar(200) not null,
    description           text,
    color                 varchar(20),
    platforms             varchar(500),
    schedule_type         varchar(30)  not null,
    days_of_week          varchar(100),
    posting_time          time         not null,
    timezone              varchar(100) not null,
    daily_post_limit      integer,
    notify_success        boolean      not null default true,
    notify_failure        boolean      not null default true,
    notify_next_reminder  boolean      not null default true,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    created_by            varchar(150)
);

create index idx_schedule_templates_org_user on schedule_templates (organization_id, user_id);

