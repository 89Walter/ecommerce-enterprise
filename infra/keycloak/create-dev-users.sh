#!/bin/bash
# Crea (o riallinea) gli utenti di sviluppo nel realm "ecommerce":
#   - un amministratore con ruolo ADMIN
#   - un cliente con il solo ruolo USER
# Le credenziali stanno nel .env della root: se mancano vengono generate e aggiunte al file.
# Uso, dalla root del progetto (Git Bash):  bash infra/keycloak/create-dev-users.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
[ -f "$ENV_FILE" ] || { echo "File .env non trovato in $ROOT_DIR (copia .env.example in .env)." >&2; exit 1; }

env_value() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | tr -d '\r'; }
random_password() { head -c 18 /dev/urandom | base64 | tr -d '/+=' | head -c 20; }
ensure_env() { # nome, valore predefinito
    if [ -z "$(env_value "$1")" ]; then
        printf '%s=%s\n' "$1" "$2" >> "$ENV_FILE"
    fi
}

if ! grep -q "^# --- Utenti di sviluppo" "$ENV_FILE"; then
    printf '\n# --- Utenti di sviluppo in Keycloak (creati da infra/keycloak/create-dev-users.sh) ---\n' >> "$ENV_FILE"
fi
ensure_env DEV_ADMIN_USERNAME admin
ensure_env DEV_ADMIN_PASSWORD "$(random_password)"
ensure_env DEV_CUSTOMER_USERNAME cliente
ensure_env DEV_CUSTOMER_PASSWORD "$(random_password)"

KEYCLOAK_URL="http://localhost:$(env_value KEYCLOAK_HOST_PORT)"
[ "$KEYCLOAK_URL" == "http://localhost:" ] && KEYCLOAK_URL="http://localhost:9000"
REALM_URL="$KEYCLOAK_URL/admin/realms/ecommerce"

json_field() { perl -MJSON::PP -e 'my $d = decode_json(do { local $/; <STDIN> }); $d = $d->[0] if ref $d eq "ARRAY"; print $d->{$ARGV[0]} // ""' "$1"; }

ADMIN_TOKEN=$(curl -sf -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=admin-cli \
    --data-urlencode "username=$(env_value KEYCLOAK_ADMIN_USERNAME)" \
    --data-urlencode "password=$(env_value KEYCLOAK_ADMIN_PASSWORD)" | json_field access_token) \
    || { echo "Keycloak non raggiungibile su $KEYCLOAK_URL: avvia l'infrastruttura con 'docker compose up -d'." >&2; exit 1; }

api() { curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' "$@"; }

upsert_user() { # username, password, nome, cognome, ruolo extra (opzionale)
    local username="$1" password="$2" first_name="$3" last_name="$4" role="${5:-}" id
    id=$(api "$REALM_URL/users?username=$username&exact=true" | json_field id)
    if [ -z "$id" ]; then
        api -X POST "$REALM_URL/users" -d "{\"username\":\"$username\",\"email\":\"$username@ecommerce.local\",\"emailVerified\":true,\"firstName\":\"$first_name\",\"lastName\":\"$last_name\",\"enabled\":true}"
        id=$(api "$REALM_URL/users?username=$username&exact=true" | json_field id)
        echo "Creato l'utente $username."
    else
        echo "L'utente $username esiste già: password riallineata al .env."
    fi
    api -X PUT "$REALM_URL/users/$id/reset-password" -d "{\"type\":\"password\",\"value\":\"$password\",\"temporary\":false}"
    if [ -n "$role" ]; then
        api -X POST "$REALM_URL/users/$id/role-mappings/realm" -d "[$(api "$REALM_URL/roles/$role")]"
    fi
}

upsert_user "$(env_value DEV_ADMIN_USERNAME)" "$(env_value DEV_ADMIN_PASSWORD)" "Admin" "E-commerce" ADMIN
upsert_user "$(env_value DEV_CUSTOMER_USERNAME)" "$(env_value DEV_CUSTOMER_PASSWORD)" "Cliente" "Prova"
echo "Fatto. Le credenziali sono nel file .env (DEV_ADMIN_* e DEV_CUSTOMER_*)."
