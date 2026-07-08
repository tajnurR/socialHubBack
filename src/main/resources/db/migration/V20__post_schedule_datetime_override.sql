alter table posts
    add column scheduled_at_override timestamp with time zone;

create index idx_posts_schedule_account_sort
    on posts (schedule_event_id, platform, social_integration_id, sort_order, scheduled_at);
