-- One database per service. They share one PostgreSQL server (and one test user) only to
-- keep MyShoppingCart light; no service reads another service's database.
-- Runs on every start (the "databases" service in docker-compose.yml), so a database added
-- later is created in an existing volume too. \gexec runs each generated statement.
SELECT format('CREATE DATABASE %I', name)
FROM unnest(ARRAY['catalog', 'orders', 'payments', 'inventory', 'notifications']) AS name
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = name)
\gexec
