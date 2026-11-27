-- Read passwords inside psql, avoiding executable scripts on Docker bind mounts.
\getenv catalog_password CATALOG_DB_PASSWORD
\getenv finance_password FINANCE_DB_PASSWORD

SELECT format('CREATE ROLE catalog_app LOGIN PASSWORD %L', :'catalog_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catalog_app') \gexec
SELECT format('CREATE ROLE finance_app LOGIN PASSWORD %L', :'finance_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finance_app') \gexec

ALTER DATABASE catalog_db OWNER TO catalog_app;
ALTER DATABASE finance_db OWNER TO finance_app;
