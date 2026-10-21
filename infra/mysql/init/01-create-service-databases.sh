#!/bin/bash
# Crea un database e un utente dedicato per ogni microservizio (database-per-service).
# MySQL esegue questo script solo alla prima inizializzazione del volume.
set -euo pipefail

run_sql() {
    mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" --database=mysql
}

create_service_database() {
    local database="$1" username="$2" password="$3"
    if [ -z "$password" ]; then
        echo "Password mancante per ${username}: impostala nel file .env" >&2
        exit 1
    fi
    run_sql <<-EOSQL
        CREATE DATABASE IF NOT EXISTS \`${database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
        CREATE USER IF NOT EXISTS '${username}'@'%' IDENTIFIED BY '${password}';
        GRANT ALL PRIVILEGES ON \`${database}\`.* TO '${username}'@'%';
EOSQL
    echo "Creato il database ${database} con l'utente ${username}."
}

create_service_database catalog_db  catalog_app  "${CATALOG_DB_PASSWORD:-}"
create_service_database order_db    order_app    "${ORDER_DB_PASSWORD:-}"
create_service_database user_db     user_app     "${USER_DB_PASSWORD:-}"
create_service_database payment_db  payment_app  "${PAYMENT_DB_PASSWORD:-}"
create_service_database shipping_db shipping_app "${SHIPPING_DB_PASSWORD:-}"
create_service_database notification_db notification_app "${NOTIFICATION_DB_PASSWORD:-}"
echo "FLUSH PRIVILEGES;" | run_sql
