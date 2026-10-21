#!/bin/sh
# Crea (o riallinea) un utente RabbitMQ per ogni microservizio, con i permessi minimi:
#   - configure: può dichiarare la topologia condivisa (exchange e code ecommerce), come fa ogni servizio all'avvio;
#   - write:     pubblica solo sull'exchange degli eventi e può solo collegare le code (bind), mai scrivere sull'exchange di default.
#                Serve anche la scrittura sul DLX: RabbitMQ la richiede per dichiarare code con x-dead-letter-exchange.
#                Il DLX instrada solo verso le code .dlq, che nessuno consuma in automatico;
#   - read:      le code del sistema: da RabbitMQ 4.3 dichiarare una coda con dead-letter richiede la lettura su quella coda,
#                e ogni servizio dichiara l'intera topologia (vedi EventTopology);
#   - topic:     sull'exchange degli eventi pubblica solo le proprie routing key (order.*, payment.*, shipping.*).
# Così chi ruba le credenziali di un servizio non può spacciarsi per un altro, ad esempio pubblicando un
# payment.completed falso con l'utente dell'order-service.
#
# Eseguito dal container "rabbitmq-setup" del docker-compose, tramite l'API HTTP di management.
set -eu

API="http://${RABBITMQ_MANAGEMENT_HOST:-rabbitmq}:15672/api"
VHOST="%2F"

# Escape minimo per inserire un valore in una stringa JSON.
json_string() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

put() { # percorso, corpo JSON
    curl -fsS -o /dev/null -u "$RABBITMQ_USERNAME:$RABBITMQ_PASSWORD" \
        -H 'Content-Type: application/json' -X PUT "$API/$1" -d "$2"
}

setup_user() { # nome servizio (order, payment, shipping), password
    service="$1"
    password="$2"
    user="$service-service"
    [ -n "$password" ] || { echo "Password mancante per $user" >&2; exit 1; }

    # Nessun tag: l'utente non può accedere alla console di management.
    put "users/$user" "{\"password\":\"$(json_string "$password")\",\"tags\":\"\"}"
    # Il punto letterale è scritto [.] e non \. : da RabbitMQ 4.3 l'API rifiuta i backslash nel JSON (errore not_json).
    put "permissions/$VHOST/$user" "{
        \"configure\": \"^(ecommerce[.]events([.]dlx)?|(order|payment|shipping|notification)-service[.].+)\$\",
        \"write\": \"^(ecommerce[.]events([.]dlx)?|(order|payment|shipping|notification)-service[.].+)\$\",
        \"read\": \"^(ecommerce[.]events([.]dlx)?|(order|payment|shipping|notification)-service[.].+)\$\"
    }"
    put "topic-permissions/$VHOST/$user" "{
        \"exchange\": \"ecommerce.events\",
        \"write\": \"^$service[.]\",
        \"read\": \".*\"
    }"
    echo "Utente RabbitMQ $user pronto."
}

# I permessi per topic richiedono che l'exchange esista già (RabbitMQ 4.3+). Stessi parametri della
# dichiarazione fatta dai servizi in EventTopology (topic, durevole), quindi le due dichiarazioni sono compatibili.
put "exchanges/$VHOST/ecommerce.events" '{"type":"topic","durable":true,"auto_delete":false,"internal":false,"arguments":{}}'

setup_user order "${ORDER_RABBITMQ_PASSWORD:-}"
setup_user payment "${PAYMENT_RABBITMQ_PASSWORD:-}"
setup_user shipping "${SHIPPING_RABBITMQ_PASSWORD:-}"
setup_user notification "${NOTIFICATION_RABBITMQ_PASSWORD:-}"
