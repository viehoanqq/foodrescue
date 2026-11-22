#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 -U postgres \
  -v catalog_password="$CATALOG_DB_PASSWORD" \
  -v order_password="$ORDER_DB_PASSWORD" \
  -v finance_password="$FINANCE_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE catalog_app LOGIN PASSWORD %L', :'catalog_password') \gexec
SELECT format('CREATE ROLE order_app LOGIN PASSWORD %L', :'order_password') \gexec
SELECT format('CREATE ROLE finance_app LOGIN PASSWORD %L', :'finance_password') \gexec
ALTER DATABASE catalog_db OWNER TO catalog_app;
ALTER DATABASE order_db OWNER TO order_app;
ALTER DATABASE finance_db OWNER TO finance_app;
SQL
