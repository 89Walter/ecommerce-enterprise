# Keycloak

Keycloak gestisce login, password e ruoli. I microservizi non vedono mai una password: ricevono un **JWT firmato** e ne verificano:

- la firma RS256, con le chiavi pubbliche JWKS di Keycloak;
- l'issuer;
- la scadenza;
- l'**audience**.

Il realm `ecommerce` viene importato all'avvio da [`infra/keycloak/ecommerce-realm.json`](../infra/keycloak/ecommerce-realm.json).

## Contenuto del realm

- **Ruoli**: `USER` (assegnato in automatico a ogni nuovo utente) e `ADMIN` (back-office: prodotti, liste admin, avanzamento spedizioni).
- **Client scope di audience**: `catalog-audience`, `order-audience`, `user-audience`, `payment-audience`, `shipping-audience`. Ognuno aggiunge al claim `aud` del token il nome del servizio corrispondente, ad esempio `order-service`. Ogni servizio accetta solo i token che contengono la propria audience.
- **Client `catalog-postman`**: pubblico, con grant *password*. Serve **solo per le prove locali** con Postman o curl.
- **Client `ecommerce-web`**: pubblico, con *Authorization Code + PKCE (S256)* e redirect verso `localhost:3000` e `localhost:5173`. È il client da usare per il futuro frontend web o mobile.

## Utenti di sviluppo

Il modo più rapido è lo script:

```bash
bash infra/keycloak/create-dev-users.sh
```

Lo script crea `admin` (ruoli `ADMIN` e `USER`) e `cliente` (solo `USER`). Le password stanno nel `.env` della root (`DEV_ADMIN_*`, `DEV_CUSTOMER_*`) e, se mancano, vengono generate. Puoi rilanciarlo quando vuoi.

### Creare altri utenti a mano

1. Avvia l'infrastruttura (`docker compose up -d`) e apri `http://localhost:9000/admin/`.
2. Accedi con `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD` del file `.env`.
3. Seleziona il realm **ecommerce** → **Users** → **Create new user**. Imposta username ed email, e se vuoi nome e cognome: lo user-service li usa per creare il profilo.
4. Nella scheda **Credentials** imposta una password con *Temporary* disattivato.
5. Per un amministratore: **Role mapping** → **Assign role** → `ADMIN`.

Non usare l'account amministratore del realm `master` per chiamare le API.

## Ottenere un token (Postman o curl)

`POST http://localhost:9000/realms/ecommerce/protocol/openid-connect/token`, body `x-www-form-urlencoded`:

| Key | Value |
|---|---|
| `grant_type` | `password` |
| `client_id` | `catalog-postman` |
| `username` | utente di prova |
| `password` | password dell'utente |
| `scope` | `openid` |

Copia `access_token` in **Authorization → Bearer Token**. Non serve richiedere gli scope di audience: sono *default client scope* e Keycloak li aggiunge da solo.

Per controllare il contenuto di un token puoi incollarlo su un decoder JWT locale; verifica che `aud` contenga i servizi e che `realm_access.roles` contenga i ruoli.

## Issuer e indirizzi

Keycloak è avviato con `--hostname=http://localhost:9000`, quindi l'issuer dei token è sempre `http://localhost:9000/realms/ecommerce`, anche quando i servizi girano in Docker:

- i servizi avviati da **IntelliJ o Maven** scaricano le chiavi da `http://localhost:9000/...`;
- i servizi avviati in **Docker** le scaricano dalla rete interna, `http://keycloak:8080/...`, ma confrontano l'issuer con `localhost:9000`.

Se cambi `KEYCLOAK_HOST_PORT`, il compose aggiorna entrambi. Se avvii i servizi da IntelliJ, imposta anche `JWT_ISSUER_URI` e `JWT_JWK_SET_URI`.

## Modificare il realm

Keycloak importa il file JSON solo se il realm **non esiste ancora** nel volume `keycloak_data`. Dopo una modifica al file hai due possibilità:

- applica la stessa modifica dalla console admin; gli utenti esistenti si conservano;
- oppure ricrea il volume, ma **cancella gli utenti creati a mano**:

```bash
docker compose rm -sf keycloak
```

```bash
docker volume rm ecommerce_keycloak_data
```

I token già emessi non cambiano: dopo ogni modifica richiedine uno nuovo.

## Produzione

Il grant password e `start-dev` sono solo per lo sviluppo. In produzione servono:

- Keycloak in modalità production, con database esterno, TLS e hostname pubblico;
- segreti gestiti da un secret manager;
- per frontend e app mobile, solo il flusso Authorization Code + PKCE (client `ecommerce-web` o equivalente), senza segreti incorporati nell'app.
