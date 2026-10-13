-- Run using the database owner before changing the application's DATABASE_URL.
-- Password is set privately using psql's interactive \password, never in this file.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nsecvpn_runtime') THEN
    CREATE ROLE nsecvpn_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
  END IF;
END $$;
ALTER ROLE nsecvpn_runtime NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
SELECT current_database() AS database_name \gset
GRANT CONNECT ON DATABASE :"database_name" TO nsecvpn_runtime;
GRANT USAGE ON SCHEMA public TO nsecvpn_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO nsecvpn_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO nsecvpn_runtime;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nsecvpn_runtime;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO nsecvpn_runtime;
ALTER ROLE nsecvpn_runtime SET statement_timeout = '15s';
ALTER ROLE nsecvpn_runtime SET idle_in_transaction_session_timeout = '30s';
-- Keep migrations on the existing owner connection. Do not make runtime the owner.
