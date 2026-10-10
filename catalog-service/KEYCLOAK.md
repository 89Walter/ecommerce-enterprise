# Keycloak locale per il Catalog Service

La configurazione Docker Compose avvia Keycloak in modalita sviluppo e importa il realm `ecommerce`. Il client `catalog-postman` abilita il grant password solo per prove locali da Postman; non riutilizzarlo in produzione o nell'app mobile. Il realm definisce i ruoli `USER` e `ADMIN` e aggiunge l'audience `catalog-service` ai token emessi per il client.

## Avvio

1. Aggiungi al file `.env` locale (ignorato da Git) le variabili Keycloak e JWT presenti in `.env.example`. Imposta una password locale robusta per `KEYCLOAK_ADMIN_PASSWORD`; conserva anche le variabili DB gia necessarie a Docker Compose.
2. Dalla cartella `catalog-service`, avvia Keycloak:

   ```powershell
   docker compose up -d keycloak
   ```

3. Apri `http://localhost:9000/admin/` e accedi con le credenziali `KEYCLOAK_ADMIN_USERNAME` e `KEYCLOAK_ADMIN_PASSWORD`.
4. Seleziona il realm `ecommerce`, crea un utente di prova, imposta una password nella scheda **Credentials** e assegna il ruolo realm `ADMIN` in **Role mapping**. Non usare l'account amministratore master di Keycloak per chiamare le API.

Keycloak e' pubblicato solo su `127.0.0.1:9000`. Il Catalog Service usa per default issuer `http://localhost:9000/realms/ecommerce`, JWKS `http://localhost:9000/realms/ecommerce/protocol/openid-connect/certs` e audience `catalog-service`. Questi valori devono restare coerenti con il realm e con i claim del token.

## Ottenere un token per Postman

In Postman crea una richiesta `POST` a:

```text
http://localhost:9000/realms/ecommerce/protocol/openid-connect/token
```

Imposta `Body` su **x-www-form-urlencoded** con questi campi:

| Key | Value |
| --- | --- |
| `grant_type` | `password` |
| `client_id` | `catalog-postman` |
| `username` | nome dell'utente di prova |
| `password` | password dell'utente |
| `scope` | `openid` |

Non aggiungere `roles` o `catalog-audience` al parametro `scope`: in questa configurazione sono client scope predefiniti e Keycloak li applica automaticamente; non sono scope da richiedere esplicitamente. La risposta include `access_token`. Copialo in **Authorization → Bearer Token** delle richieste Catalog Service. L'utente `ADMIN` puo' quindi inviare `POST /api/v1/catalog/products/insertProduct`, `PUT /api/v1/catalog/products/{id}` e `DELETE /api/v1/catalog/products/{id}`. Per un token con solo `USER`, le scritture devono rispondere `403`; senza token valido, `401`.

## Produzione e app mobile

Il grant password e `start-dev` sono esclusivamente per lo sviluppo locale. Per un'app mobile crea un client pubblico dedicato con **Authorization Code + PKCE (S256)**, redirect URI esplicite e HTTPS; non incorporare segreti client nell'app. In produzione usa il deployment Keycloak in modalita production con database esterno supportato, TLS, hostname pubblico, backup e credenziali gestite da un secret manager. Configura nel Catalog Service gli URL effettivi di issuer e JWKS e l'audience prevista per ambiente.
