#!/bin/sh
set -eu
mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS 'identity_app'@'%' IDENTIFIED BY '$IDENTITY_DB_PASSWORD';
CREATE USER IF NOT EXISTS 'store_app'@'%' IDENTIFIED BY '$STORE_DB_PASSWORD';
CREATE USER IF NOT EXISTS 'order_app'@'%' IDENTIFIED BY '$ORDER_DB_PASSWORD';
GRANT ALL PRIVILEGES ON identity_db.* TO 'identity_app'@'%';
GRANT ALL PRIVILEGES ON store_db.* TO 'store_app'@'%';
GRANT ALL PRIVILEGES ON order_db.* TO 'order_app'@'%';
SQL
