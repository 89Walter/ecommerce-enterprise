# Gateway Service

API Gateway reattivo, basato su Spring Cloud Gateway WebFlux, in ascolto sulla porta `8080`. È il punto d'ingresso unico per i client: non ha un database e non gestisce il login, che avviene direttamente con Keycloak.

## Rotte

| Percorso | Servizio |
|---|---|
| `/api/v1/catalog/**` | catalog-service (8081) |
| `/api/v1/orders/**` | order-service (8082) |
| `/api/v1/users/**` | user-service (8083) |
| `/api/v1/payments/**` | payment-service (8084) |
| `/api/v1/shipments/**` | shipping-service (8085) |

## Sicurezza

Il gateway verifica la firma, l'issuer e la scadenza del JWT, e soprattutto **l'audience del servizio di destinazione**. `PathBasedAuthenticationManagerResolver` sceglie il validatore in base al percorso: per esempio un token senza `payment-service` in `aud` viene respinto su `/api/v1/payments/**`.

Regole di accesso:

- lettura del catalogo: pubblica;
- scritture sul catalogo e tutti i percorsi `/admin/**`: ruolo `ADMIN`;
- resto delle API: utente autenticato;
- percorsi non instradati: negati;
- Actuator: è pubblico solo `/actuator/health`.

I microservizi ripetono comunque i controlli: il gateway non è l'unico confine di sicurezza.

## Resilienza

- Timeout: 2 s per la connessione, 5 s per la risposta.
- Le `GET` vengono ritentate una volta su 502, 503 o 504; le scritture non vengono mai ritentate.
- Ogni servizio ha un **circuit breaker** separato: dopo errori ripetuti il gateway risponde subito `503` con un JSON di fallback, invece di attendere.
- Il corpo di una richiesta non può superare 2 MB.
- CORS: sono ammesse le origini in `CORS_ALLOWED_ORIGINS`, predefinite `localhost:3000` e `localhost:5173`.
