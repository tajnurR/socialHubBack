#!/usr/bin/env bash
#
# init-db.sh — provision the PostgreSQL role + database SocialHub expects.
#
# Idempotent: safe to run repeatedly. Run it again whenever you change the DB
# name/user/password so Postgres matches what the app uses.
#
# Configuration (env vars; defaults match back/src/main/resources/application.yml):
#   DB_NAME       database name        (default: socialhub)
#   DB_USERNAME   login role           (default: socialhub)
#   DB_PASSWORD   role password        (default: socialhub)
#   PG_SUPERUSER  admin role to run as (default: postgres, via sudo)
#
# Usage:
#   ./scripts/init-db.sh
#   DB_NAME=socialhub_prod DB_USERNAME=app DB_PASSWORD=secret ./scripts/init-db.sh
#
# Requires sudo access to the postgres OS user (it will prompt for your password).
set -euo pipefail

DB_NAME="${DB_NAME:-socialhub}"
DB_USERNAME="${DB_USERNAME:-socialhub}"
DB_PASSWORD="${DB_PASSWORD:-socialhub}"
PG_SUPERUSER="${PG_SUPERUSER:-postgres}"

echo "==> Provisioning PostgreSQL"
echo "    database : ${DB_NAME}"
echo "    role     : ${DB_USERNAME}"
echo "    superuser: ${PG_SUPERUSER}"

# Run a SQL statement as the Postgres superuser.
run_sql() {
  sudo -u "${PG_SUPERUSER}" psql -v ON_ERROR_STOP=1 "$@"
}

# 1. Create the login role, or reset its password if it already exists.
#    Password is passed via a psql variable so it is properly quoted/escaped.
echo "==> Ensuring role '${DB_USERNAME}' exists with the configured password"
run_sql -v role="${DB_USERNAME}" -v pw="${DB_PASSWORD}" <<'SQL'
SELECT format(
  CASE WHEN EXISTS (SELECT FROM pg_roles WHERE rolname = :'role')
       THEN 'ALTER ROLE %I WITH LOGIN PASSWORD %L'
       ELSE 'CREATE ROLE %I WITH LOGIN PASSWORD %L'
  END, :'role', :'pw')
\gexec
SQL

# 2. Create the database owned by the role (only if missing — createdb has no IF NOT EXISTS).
if run_sql -tAc "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" | grep -q 1; then
  echo "==> Database '${DB_NAME}' already exists — leaving it as is"
else
  echo "==> Creating database '${DB_NAME}' owned by '${DB_USERNAME}'"
  sudo -u "${PG_SUPERUSER}" createdb -O "${DB_USERNAME}" "${DB_NAME}"
fi

# 3. Make sure the role can use/create objects in the public schema (PG 15+ hardening).
echo "==> Granting schema privileges to '${DB_USERNAME}'"
run_sql -d "${DB_NAME}" -v role="${DB_USERNAME}" <<'SQL'
GRANT ALL ON SCHEMA public TO :"role";
SQL

# 4. Verify the role can actually log in with the configured password.
echo "==> Verifying connection"
if PGPASSWORD="${DB_PASSWORD}" psql -h localhost -U "${DB_USERNAME}" -d "${DB_NAME}" -tAc "SELECT 'ok'" | grep -q ok; then
  echo "==> Success: ${DB_USERNAME}@localhost/${DB_NAME} is ready."
  echo "    Start the app with: ./mvnw spring-boot:run   (Flyway applies migrations)"
else
  echo "!!  Connection check failed — review pg_hba.conf auth method for localhost." >&2
  exit 1
fi
