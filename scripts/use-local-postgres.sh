#!/usr/bin/env bash
# One-time setup for running the shop against a PostgreSQL installed on this machine instead of
# the postgres container. Creates the login role the services use (POSTGRES_USER and
# POSTGRES_PASSWORD in .env) with the right to create its own databases, so the services never
# need the superuser's password. Asks for the password of the local superuser "postgres".
# Run from the repository root; then set POSTGRES_HOST and COMPOSE_PROFILES in .env (see README).
set -euo pipefail
source .env

[ -n "${PGPASSWORD:-}" ] || read -rsp "Password of the local PostgreSQL superuser 'postgres': " PGPASSWORD
export PGPASSWORD

# psql runs in a container, so nothing needs to be on the PATH; \gexec runs the generated statement.
MSYS_NO_PATHCONV=1 docker run --rm -i -e PGPASSWORD postgres:17-alpine \
  psql -h host.docker.internal -U postgres -d postgres -v ON_ERROR_STOP=1 -q \
  -v role="${POSTGRES_USER:-shop}" -v pw="$POSTGRES_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN CREATEDB PASSWORD %L', :'role', :'pw')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'role')
\gexec
SQL
echo
echo "Role ${POSTGRES_USER:-shop} is ready on the local PostgreSQL."
