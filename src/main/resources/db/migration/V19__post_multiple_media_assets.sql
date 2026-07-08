create table post_media_assets (
    id bigserial primary key,
    post_id bigint not null references posts(id) on delete cascade,
    media_asset_id bigint not null references media_assets(id),
    display_order integer not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    created_by varchar(255),
    constraint uq_post_media_assets_post_media unique (post_id, media_asset_id)
);

create index idx_post_media_assets_post on post_media_assets (post_id, display_order);
create index idx_post_media_assets_media on post_media_assets (media_asset_id);

insert into post_media_assets (post_id, media_asset_id, display_order, created_at, updated_at, created_by)
select id, media_asset_id, 0, coalesce(created_at, now()), coalesce(updated_at, now()), created_by
from posts
where media_asset_id is not null
on conflict do nothing;
