# E-commerce a microservizi

Backend di un e-commerce in **Java 17 + Spring Boot 4**, diviso in microservizi indipendenti che comunicano via HTTP (sincrono) e tramite **eventi su RabbitMQ** (asincrono). L'autenticazione è delegata a **Keycloak** (OAuth2/OIDC, token JWT).

```
                         ┌───────────────────────┐
  Client web / mobile ──►│   gateway-service     │  JWT (firma, issuer, audience), CORS,
  (Bearer token)         │   :8080               │  retry, circuit breaker, routing
                         └──┬────┬────┬────┬────┬┘
         ┌──────────────────┘    │    │    │    └──────────────────┐
         ▼                       ▼    │    ▼                       ▼
 ┌───────────────┐  HTTP  ┌──────────┐│ ┌────────────┐     ┌──────────────┐
 │ catalog :8081 │◄───────│ order    ││ │ payment    │     │ shipping     │
 │ prodotti      │        │ :8082    ││ │ :8084      │     │ :8085        │
 └──┬─────────┬──┘        └────┬─────┘│ └─────┬──────┘     └──────┬───────┘
    │         │                │      ▼       │                   │
 MySQL     Redis            MySQL ┌──────────┐│                   │
 catalog_db (cache)        order_db│ user     ││ MySQL           MySQL
                                  │ :8083    ││ payment_db      shipping_db
                                  └────┬─────┘│
                                    MySQL user_db
                 ▲ eventi            ▲ eventi           ▲ eventi
                 └──────────────── RabbitMQ (exchange "ecommerce.events") ──┘

                 Keycloak :9000 — realm "ecommerce", ruoli USER / ADMIN
```

| Servizio | Porta | Base path | Responsabilità |
|---|---|---|---|
| `gateway-service` | 8080 | `/api/v1/**` | Punto d'ingresso unico, sicurezza perimetrale |
| `catalog-service` | 8081 | `/api/v1/catalog` | Prodotti, ricerca, cache Redis |
| `order-service` | 8082 | `/api/v1/orders` | Ordini e loro ciclo di vita |
| `user-service` | 8083 | `/api/v1/users` | Profilo cliente (indirizzo predefinito, telefono) |
| `payment-service` | 8084 | `/api/v1/payments` | Addebiti e rimborsi (gateway simulato) |
| `shipping-service` | 8085 | `/api/v1/shipments` | Spedizioni e tracking |

Ogni servizio ha **il proprio database** (`catalog_db`, `order_db`, …) con un utente MySQL dedicato che vede solo quello: nessun servizio legge le tabelle di un altro.

## Il flusso di un ordine

```
cliente ── POST /orders ──► order-service ── ORDER_PLACED ──► payment-service
                             (PLACED)                          addebita
                                 ▲                                 │
                                 └──── PAYMENT_COMPLETED ──────────┤
                             (CONFIRMED) ── ORDER_CONFIRMED ──► shipping-service
                                 ▲                                crea la spedizione
                                 │                                 │ ADMIN: SHIPPED, DELIVERED
                                 └──── SHIPPING_UPDATED ───────────┘
                             (SHIPPED → DELIVERED)
```

- **Pagamento rifiutato** → `PAYMENT_FAILED` → ordine `CANCELLED`.
- **Annullamento del cliente** (in `PLACED` o `CONFIRMED`) → `ORDER_CANCELLED` → il pagamento viene rimborsato e la spedizione annullata.

I dettagli (contratti degli eventi, garanzie di consegna, casi limite) sono in [docs/events.md](docs/events.md).

## Avvio rapido

Prerequisiti: Docker Desktop, JDK 17, Maven 3.9.

Il modo standard di lavorare è: **infrastruttura in Docker, microservizi da IntelliJ**.

### 1. Configurazione (una volta sola)

Tutta la configurazione locale sta in un solo file, il `.env` della root, ignorato da Git. Se non esiste, crealo dal modello e sostituisci le password:

```bash
cp .env.example .env
```

Ogni servizio lo legge da solo all'avvio (`spring.config.import`), quindi le Run Configuration di IntelliJ non hanno bisogno di variabili d'ambiente. I servizi non hanno più un `.env` proprio.

### 2. Avvia l'infrastruttura

```bash
docker compose up -d
```

Questo comando avvia MySQL (5 database), Redis, RabbitMQ e Keycloak. Puoi lanciarlo anche da IntelliJ con la Run Configuration **infrastruttura (docker compose up)**.

### 3. Crea gli utenti di sviluppo (la prima volta, o dopo aver azzerato Keycloak)

```bash
bash infra/keycloak/create-dev-users.sh
```

Lo script crea due utenti nel realm `ecommerce`, `admin` (ruoli `ADMIN` e `USER`) e `cliente` (solo `USER`). Le password vengono generate e salvate nel `.env` (`DEV_ADMIN_*`, `DEV_CUSTOMER_*`). Puoi rilanciarlo quando vuoi: riallinea gli utenti al `.env`.

### 4. Avvia i microservizi da IntelliJ

Nel menu delle Run Configuration, cartella **E-commerce**:

| Configurazione | Cosa avvia |
|---|---|
| `backend (tutti i servizi)` | tutti e 6 insieme |
| `catalog-service` … `gateway-service` | un servizio alla volta, anche in debug |

Ognuna usa come working directory la cartella del proprio servizio e il JDK 17. Non servono tutti i servizi: per il flusso completo di un ordine bastano catalog, order, payment e shipping, più il gateway se chiami le API da lì. Gli eventi destinati a un servizio spento restano in coda su RabbitMQ e vengono elaborati appena lo avvii.

Senza IntelliJ, lo stesso risultato si ottiene con Maven:

```bash
mvn -f order-service/pom.xml spring-boot:run
```

### 5. Ottieni un token

Usa le credenziali `DEV_ADMIN_*` o `DEV_CUSTOMER_*` del `.env`, con il client di sviluppo `catalog-postman` (creato da `create-dev-users.sh`, non presente in produzione):

```bash
curl.exe -X POST http://localhost:9000/realms/ecommerce/protocol/openid-connect/token -d grant_type=password -d client_id=catalog-postman -d scope=openid -d username=admin -d password=PASSWORD
```

Su PowerShell usa `curl.exe`; su Git Bash basta `curl`. Il token contiene le audience di tutti e cinque i servizi: usalo come **Bearer Token** sulle chiamate a `http://localhost:8080/api/v1/...`. Più dettagli, e la configurazione per un frontend reale (client `ecommerce-web` con PKCE), sono in [docs/keycloak.md](docs/keycloak.md).

### Spegnere e azzerare

Per fermare l'infrastruttura conservando i dati:

```bash
docker compose down
```

Per cancellare **tutti** i dati (database, code, utenti Keycloak), poi rilancia il punto 3:

```bash
docker compose down -v
```

### In alternativa: tutto in Docker

Il profilo `apps` compila e avvia in container anche i 6 microservizi e il frontend Angular (`http://localhost:3000`), che viene costruito dalla cartella `../e-commerce-FE`. È utile per provare il sistema completo senza IntelliJ; in quel caso non avviarli anche da IntelliJ o con `npm start`, perché userebbero le stesse porte.

```bash
docker compose --profile apps up -d --build
```

## API principali (tramite gateway)

| Metodo | Percorso | Accesso |
|---|---|---|
| `GET` | `/api/v1/catalog/products?search=&page=&size=` | pubblico |
| `GET` | `/api/v1/catalog/products/{id}` | pubblico (in cache) |
| `POST` / `PUT` / `DELETE` | `/api/v1/catalog/products[/{id}]` | ADMIN |
| `POST` | `/api/v1/orders` | utente |
| `GET` | `/api/v1/orders`, `/api/v1/orders/{id}` | utente (solo i propri) |
| `POST` | `/api/v1/orders/{id}/cancel` | utente |
| `GET` | `/api/v1/orders/admin?status=` | ADMIN |
| `GET` / `PUT` | `/api/v1/users/me` | utente |
| `GET` | `/api/v1/users/admin`, `/api/v1/users/admin/{id}` | ADMIN |
| `GET` | `/api/v1/payments/orders/{orderId}` | proprietario o ADMIN |
| `GET` | `/api/v1/payments/admin?status=` | ADMIN |
| `GET` | `/api/v1/shipments/orders/{orderId}` | proprietario o ADMIN |
| `GET` | `/api/v1/shipments/admin?status=` | ADMIN |
| `PATCH` | `/api/v1/shipments/admin/{id}/status` | ADMIN (`SHIPPED`, poi `DELIVERED`) |

Ogni servizio pubblica la propria Swagger UI, ad esempio `http://localhost:8080/api/v1/orders/swagger-ui/index.html`. Il pagamento simulato rifiuta gli ordini sopra `PAYMENT_MAX_APPROVED_AMOUNT` (predefinito 5000 €), così puoi provare anche il percorso di errore.

## Frontend

La SPA Angular (catalogo, carrello, ordini, profilo e back-office ADMIN) è un progetto separato, nella cartella `e-commerce-FE` accanto a questa ([89Walter/ecommerce-enterprise-FE](https://github.com/89Walter/ecommerce-enterprise-FE)). Usa il login Keycloak tramite il client `ecommerce-web`. Due modi per avviarla, entrambi su `http://localhost:3000`:

- **in Docker**, insieme al back-end: è il servizio `frontend` del profilo `apps`. Si può anche ricostruire e avviare da solo, se gateway e Keycloak sono già attivi:

  ```bash
  docker compose --profile apps up -d --build --no-deps frontend
  ```

  Il container riceve gli indirizzi pubblici di gateway e Keycloak da `GATEWAY_HOST_PORT` e `KEYCLOAK_HOST_PORT`, quindi segue il `.env` senza essere ricompilato. `FRONTEND_HOST_PORT` deve restare tra le origini di `CORS_ALLOWED_ORIGINS` e i redirect URI del client `ecommerce-web`.

- **in sviluppo**, con ricaricamento automatico a ogni modifica:

  ```bash
  cd ../e-commerce-FE && npm install && npm start
  ```

Dettagli nel README di quel progetto.

## Strumenti di sviluppo

| Cosa | Dove |
|---|---|
| Console RabbitMQ (code, DLQ, messaggi) | `http://localhost:15672` (credenziali `RABBITMQ_*`) |
| Console Keycloak | `http://localhost:9000/admin/` |
| Health di un servizio | `http://localhost:8082/api/v1/orders/actuator/health` |
| Metriche Prometheus | `…/actuator/prometheus` di ogni servizio |

## Test

Dalla root, compila e testa tutti i servizi:

```bash
mvn test
```

I test usano H2 in modalità MySQL e non richiedono Docker.

## Struttura del repository

```
.
├── docker-compose.yml        # infrastruttura + servizi (profilo "apps")
├── .env.example              # variabili da copiare in .env
├── pom.xml                   # aggregatore Maven (mvn test su tutto)
├── infra/
│   ├── keycloak/             # realm importato all'avvio
│   └── mysql/init/           # crea un database e un utente per servizio
├── docs/                     # eventi, Keycloak
├── catalog-service/  order-service/  user-service/
└── payment-service/  shipping-service/  gateway-service/
```

Dentro ogni servizio il codice è organizzato per funzionalità: `product/`, `order/`, `payment/`, …, più `config/`, `exception/` e, nei servizi che usano gli eventi, `messaging/`.

## Cosa manca per la produzione

Questa è una base locale completa, non un'installazione di produzione. Servirebbero ancora:

- TLS;
- Keycloak in modalità production con un database esterno;
- un secret manager;
- osservabilità centralizzata (tracing, log aggregati);
- rate limiting;
- backup.

## Sicurezza

Oltre a JWT validati (firma RS256, issuer e audience) sia dal gateway sia da ogni servizio:

- **RabbitMQ**: ogni servizio ha il proprio utente (`ORDER_/PAYMENT_/SHIPPING_RABBITMQ_PASSWORD`), creato da `infra/rabbitmq/setup-service-users.sh` tramite il container `rabbitmq-setup`. Può pubblicare solo le proprie routing key (`order.*`, `payment.*`, `shipping.*`) e non può scrivere direttamente nelle code: chi ruba le credenziali dell'order-service non può pubblicare un `payment.completed` falso. In più l'order-service conferma un ordine solo se importo e valuta del pagamento coincidono con il totale.
- **Rate limiting** nel gateway, su Redis: `GATEWAY_RATE_LIMIT_PER_SECOND` richieste al secondo (picco `GATEWAY_RATE_LIMIT_BURST`) per utente, o per IP se anonimo. Oltre il limite risponde `429`.
- **Swagger/OpenAPI** disattivabile con `API_DOCS_ENABLED=false`, da usare in produzione.
- **Keycloak**: password policy, protezione brute force e rotazione dei refresh token (vedi [docs/keycloak.md](docs/keycloak.md)).
- **Frontend**: nginx invia Content-Security-Policy, `X-Frame-Options`, `Referrer-Policy` e `Permissions-Policy`; HSTS con `HSTS_ENABLED=true` quando è servito in HTTPS.
- **Dipendenze**: Dependabot (`.github/dependabot.yml`) propone gli aggiornamenti di Maven e delle immagini Docker.

Prima di andare in produzione restano da fare: Keycloak in modalità `start` con HTTPS e un database dedicato, TLS davanti a gateway e frontend, password vere al posto di quelle di `.env.example`.
