-- =============================================================================
-- V13: User-owned media library backed by Google Drive.
--
-- Metadata is stored locally for search/filter/export. The file bytes are uploaded
-- directly to the connected Google Drive account; the app server is not primary
-- storage for media.
-- =============================================================================

create table media_assets (
    id                    bigint generated always as identity primary key,
    organization_id       bigint        not null references organizations (id),
    user_id               bigint        not null references users (id),
    file_name             varchar(500)  not null,
    original_file_name    varchar(500)  not null,
    media_type            varchar(20)   not null,
    content_type          varchar(120)  not null,
    extension             varchar(20)   not null,
    file_size             bigint        not null,
    checksum_sha256       varchar(64)   not null,
    google_drive_file_id  varchar(255),
    google_drive_url      varchar(2000),
    direct_download_url   varchar(2000),
    thumbnail_url         varchar(2000),
    upload_status         varchar(30)   not null,
    error_message         varchar(1000),
    created_at            timestamptz   not null default now(),
    updated_at            timestamptz   not null default now(),
    created_by            varchar(150),
    constraint uq_media_assets_owner_checksum_size
        unique (organization_id, user_id, checksum_sha256, file_size)
);

create index idx_media_assets_org_user_created
    on media_assets (organization_id, user_id, created_at desc);

create index idx_media_assets_org_user_type
    on media_assets (organization_id, user_id, media_type);

create index idx_media_assets_org_user_status
    on media_assets (organization_id, user_id, upload_status);
