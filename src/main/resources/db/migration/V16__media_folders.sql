-- =============================================================================
-- V16: Media folders mapped to Google Drive folders.
-- =============================================================================

create table media_folders (
    id                   bigint generated always as identity primary key,
    organization_id      bigint       not null references organizations (id),
    user_id              bigint       not null references users (id),
    name                 varchar(200) not null,
    google_drive_folder_id varchar(255) not null,
    google_drive_url     varchar(2000),
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),
    created_by           varchar(150),
    constraint uq_media_folders_owner_name unique (organization_id, user_id, name),
    constraint uq_media_folders_drive_id unique (google_drive_folder_id)
);

create index idx_media_folders_org_user on media_folders (organization_id, user_id);

alter table media_assets
    add column folder_id bigint references media_folders (id);

create index idx_media_assets_folder on media_assets (folder_id);
