-- Store the validated native media target for scheduled/published posts.
alter table posts
    add column media_type varchar(20);
