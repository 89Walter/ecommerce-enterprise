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
    api -X PUT "$REALM_URL/users/$id/reset-password" -d "{\"type\":\"password\",\"value\":\"$password\",\"temporary\":false}" \
        || { echo "Password di $username rifiutata: deve rispettare la password policy del realm (almeno 12 caratteri, diversa da username ed email)." >&2; exit 1; }
    if [ -n "$role" ]; then
        api -X POST "$REALM_URL/users/$id/role-mappings/realm" -d "[$(api "$REALM_URL/roles/$role")]"
    fi
}

# Il realm viene importato solo al primo avvio di Keycloak: riapplica le impostazioni di sicurezza
# (password policy, blocco dei tentativi, durata dei token...) anche a un realm creato in precedenza.
REALM_SECURITY_SETTINGS=$(perl -MJSON::PP -e '
    my $realm = decode_json(do { local $/; <STDIN> });
    my @keys = qw(sslRequired duplicateEmailsAllowed resetPasswordAllowed verifyEmail smtpServer passwordPolicy bruteForceProtected permanentLockout failureFactor
                  waitIncrementSeconds maxFailureWaitSeconds maxDeltaTimeSeconds quickLoginCheckMilliSeconds
                  minimumQuickLoginWaitSeconds accessTokenLifespan ssoSessionIdleTimeout ssoSessionMaxLifespan
                  revokeRefreshToken refreshTokenMaxReuse);
    print encode_json({ map { exists $realm->{$_} ? ($_ => $realm->{$_}) : () } @keys });
' < "$ROOT_DIR/infra/keycloak/ecommerce-realm.json")
api -X PUT "$REALM_URL" -d "$REALM_SECURITY_SETTINGS"
echo "Impostazioni di sicurezza del realm riallineate."

# Ruoli assegnati a ogni utente: oltre a USER servono quelli standard del client "account", senza i quali
# la pagina "Gestisci account e password" di Keycloak risponde 403. Vale subito anche per gli utenti esistenti.
DEFAULT_ROLE_URL="$REALM_URL/roles/default-roles-ecommerce/composites"
ACCOUNT_CLIENT_ID=$(api "$REALM_URL/clients?clientId=account" | json_field id)
DEFAULT_COMPOSITES=$(perl -MJSON::PP -e '
    my ($realm_roles, @client_roles) = map { decode_json($_) } @ARGV;
    my @wanted_realm = grep { my $n = $_->{name}; grep { $_ eq $n } qw(USER offline_access uma_authorization) } @$realm_roles;
    my @wanted_client = grep { my $n = $_->{name}; grep { $_ eq $n } qw(manage-account view-profile) } @{$client_roles[0]};
    print encode_json([@wanted_realm, @wanted_client]);
' "$(api "$REALM_URL/roles")" "$(api "$REALM_URL/clients/$ACCOUNT_CLIENT_ID/roles")")
api -X POST "$DEFAULT_ROLE_URL" -d "$DEFAULT_COMPOSITES"
echo "Ruoli predefiniti degli utenti riallineati."

scope_id_by_name() {
    api "$REALM_URL/client-scopes" | perl -MJSON::PP -e '
        print map { $_->{name} eq $ARGV[0] ? $_->{id} : () } @{decode_json(do { local $/; <STDIN> })}' "$1"
}

# Lo scope "roles" deve contenere, oltre ai ruoli del realm, anche i ruoli dei client e l'audience relativa:
# la console account di Keycloak ("Gestisci account e password") li richiede, altrimenti risponde 403.
ROLES_SCOPE_ID=$(scope_id_by_name roles)
EXISTING_MAPPERS=$(api "$REALM_URL/client-scopes/$ROLES_SCOPE_ID/protocol-mappers/models")
perl -MJSON::PP -e '
    my %existing = map { $_->{name} => 1 } @{decode_json($ARGV[0])};
    my $realm = decode_json(do { local $/; <STDIN> });
    my ($roles) = grep { $_->{name} eq "roles" } @{$realm->{clientScopes}};
    print encode_json($_), "\n" for grep { !$existing{$_->{name}} } @{$roles->{protocolMappers}};
' "$EXISTING_MAPPERS" < "$ROOT_DIR/infra/keycloak/ecommerce-realm.json" | while IFS= read -r mapper; do
    api -X POST "$REALM_URL/client-scopes/$ROLES_SCOPE_ID/protocol-mappers/models" -d "$mapper"
done

# Scope predefiniti del realm e dei client integrati di Keycloak (account, account-console): senza "basic" e
# "roles" i loro token non hanno né l'utente né i ruoli.
for scope in basic roles profile email; do
    scope_id=$(scope_id_by_name "$scope")
    api -X PUT "$REALM_URL/default-default-client-scopes/$scope_id"
    for builtin_client in account account-console; do
        builtin_id=$(api "$REALM_URL/clients?clientId=$builtin_client" | json_field id)
        [ -n "$builtin_id" ] && api -X PUT "$REALM_URL/clients/$builtin_id/default-client-scopes/$scope_id"
    done
done
echo "Scope della console account riallineati."

AUDIENCE_SERVICES="catalog order user payment shipping assistant"

# Uno scope di audience aggiunto al realm JSON dopo il primo avvio (es. assistant-audience) non esiste ancora
# in Keycloak: lo crea dalla definizione del file.
for service in $AUDIENCE_SERVICES; do
    scope_name="$service-audience"
    if [ -z "$(api "$REALM_URL/client-scopes" | perl -MJSON::PP -e '
            print map { $_->{name} eq $ARGV[0] ? $_->{id} : () } @{decode_json(do { local $/; <STDIN> })}' "$scope_name")" ]; then
        scope_json=$(perl -MJSON::PP -e '
            my $realm = decode_json(do { local $/; <STDIN> });
            my ($scope) = grep { $_->{name} eq $ARGV[0] } @{$realm->{clientScopes}};
            print encode_json($scope) if $scope' "$scope_name" < "$ROOT_DIR/infra/keycloak/ecommerce-realm.json")
        [ -n "$scope_json" ] || continue
        api -X POST "$REALM_URL/client-scopes" -d "$scope_json"
        echo "Creato lo scope $scope_name."
    fi
done

# Ogni client riceve tutti gli scope di audience come scope predefiniti (operazione idempotente).
assign_audience_scopes() { # clientId
    local client_id scope_id
    client_id=$(api "$REALM_URL/clients?clientId=$1" | json_field id)
    [ -n "$client_id" ] || return 0
    for service in $AUDIENCE_SERVICES; do
        scope_id=$(api "$REALM_URL/client-scopes" | perl -MJSON::PP -e '
            print map { $_->{name} eq $ARGV[0] ? $_->{id} : () } @{decode_json(do { local $/; <STDIN> })}' "$service-audience")
        [ -n "$scope_id" ] && api -X PUT "$REALM_URL/clients/$client_id/default-client-scopes/$scope_id"
    done
}

# I mapper di audience usano "included.custom.audience": "included.client.audience" punta a un client che
# non esiste (i servizi non sono client Keycloak) e da Keycloak 26.4 viene ignorato, lasciando i token senza "aud".
for service in $AUDIENCE_SERVICES; do
    scope_id=$(api "$REALM_URL/client-scopes" | perl -MJSON::PP -e '
        print map { $_->{name} eq $ARGV[0] ? $_->{id} : () } @{decode_json(do { local $/; <STDIN> })}' "$service-audience")
    [ -n "$scope_id" ] || continue
    mapper=$(api "$REALM_URL/client-scopes/$scope_id/protocol-mappers/models" | perl -MJSON::PP -e '
        my ($m) = grep { $_->{protocolMapper} eq "oidc-audience-mapper" } @{decode_json(do { local $/; <STDIN> })};
        exit unless $m;
        $m->{config} = { "included.custom.audience" => "$ARGV[0]-service", "id.token.claim" => "false", "access.token.claim" => "true" };
        print encode_json($m)' "$service")
    [ -n "$mapper" ] || continue
    mapper_id=$(printf '%s' "$mapper" | json_field id)
    api -X PUT "$REALM_URL/client-scopes/$scope_id/protocol-mappers/models/$mapper_id" -d "$mapper"
done
echo "Mapper di audience riallineati."

# Client con password grant, comodo per Postman e curl. Esiste SOLO negli ambienti creati da questo script:
# non è nel realm importato, così in produzione non c'è un endpoint per provare password senza la pagina di login.
if [ -z "$(api "$REALM_URL/clients?clientId=catalog-postman" | json_field id)" ]; then
    api -X POST "$REALM_URL/clients" -d '{
        "clientId": "catalog-postman",
        "name": "E-commerce API local Postman client",
        "description": "Password grant for local testing only. Never use it in production or in a frontend.",
        "enabled": true,
        "protocol": "openid-connect",
        "publicClient": true,
        "standardFlowEnabled": false,
        "implicitFlowEnabled": false,
        "directAccessGrantsEnabled": true,
        "serviceAccountsEnabled": false,
        "defaultClientScopes": ["basic", "roles", "catalog-audience", "order-audience", "user-audience",
                                "payment-audience", "shipping-audience", "assistant-audience", "profile", "email"]
    }'
    echo "Creato il client di sviluppo catalog-postman."
fi
assign_audience_scopes ecommerce-web
assign_audience_scopes catalog-postman
echo "Scope di audience assegnati ai client."

upsert_user "$(env_value DEV_ADMIN_USERNAME)" "$(env_value DEV_ADMIN_PASSWORD)" "Admin" "E-commerce" ADMIN
upsert_user "$(env_value DEV_CUSTOMER_USERNAME)" "$(env_value DEV_CUSTOMER_PASSWORD)" "Cliente" "Prova"
echo "Fatto. Le credenziali sono nel file .env (DEV_ADMIN_* e DEV_CUSTOMER_*)."
