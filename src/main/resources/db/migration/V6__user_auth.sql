-- =============================================================================
-- V6: Local username/password authentication.
--
-- Adds a BCrypt password hash to users and makes email globally unique (login
-- resolves the user by email across the whole system, then reads their org from
-- the row). The initial admin is seeded on startup by AuthSeeder (so no BCrypt
-- hash is hardcoded here).
-- =============================================================================

alter table users add column password_hash varchar(100);

alter table users drop constraint uq_users_org_email;
alter table users add constraint uq_users_email unique (email);
