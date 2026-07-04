-- Link draft/scheduled posts to media-library assets so media metadata and
-- upload status are stored separately from post content.
alter table posts
    add column media_asset_id bigint references media_assets (id);

create index idx_posts_media_asset
    on posts (media_asset_id);
