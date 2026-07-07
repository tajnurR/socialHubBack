alter table schedule_events
    add column target_platform varchar(30),
    add column social_integration_id bigint,
    add column custom_interval_hours integer;

update schedule_events
   set target_platform = split_part(coalesce(nullif(platforms, ''), 'FACEBOOK'), ',', 1),
       custom_interval_hours = coalesce(interval_hours, 1)
 where target_platform is null;

alter table schedule_events
    add constraint fk_schedule_events_social_integration
        foreign key (social_integration_id) references social_integrations (id);
