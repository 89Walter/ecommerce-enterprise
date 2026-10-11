# Eventi e comunicazione asincrona

I servizi non si chiamano tra loro per far avanzare un ordine: pubblicano **eventi** su RabbitMQ e reagiscono agli eventi degli altri. È una *coreografia*: nessun servizio centrale dirige il processo.

L'unica chiamata sincrona è `order-service → catalog-service` (`GET /products/{id}`). Serve a leggere nome e prezzo correnti al momento dell'ordine.

## Topologia RabbitMQ

- **Exchange** `ecommerce.events` (topic, durevole): riceve tutti gli eventi; la routing key indica il tipo.
- **Exchange** `ecommerce.events.dlx` (direct): riceve i messaggi scartati.
- C'è **una coda per ogni coppia servizio consumatore + tipo di evento**, con nome `<servizio>.<evento>`, e ognuna ha una dead-letter queue `<coda>.dlq`.
- **Ogni servizio dichiara all'avvio l'intera topologia**: così un evento trova sempre la sua coda e aspetta lì anche se chi lo consuma non è ancora partito.

| Routing key | Pubblicato da | Code che lo ricevono |
|---|---|---|
| `order.placed` | order | `payment-service.order-placed` |
| `order.confirmed` | order | `shipping-service.order-confirmed` |
| `order.cancelled` | order | `payment-service.order-cancelled`, `shipping-service.order-cancelled` |
| `payment.completed` | payment | `order-service.payment-completed` |
| `payment.failed` | payment | `order-service.payment-failed` |
| `payment.refunded` | payment | `order-service.payment-refunded` (registra la data del rimborso) |
| `shipping.updated` | shipping | `order-service.shipping-updated` |

Le costanti e l'elenco delle code stanno in `EventTopology`, una classe identica in ogni servizio.

## Contratti (JSON)

Tutti gli eventi hanno `eventId` (UUID) e `occurredAt` (ISO-8601 UTC). I messaggi non contengono nomi di classi Java: ogni servizio ha i propri `record` e ignora i campi che non usa. Così un servizio può aggiungere campi senza rompere gli altri.

```jsonc
// order.placed
{ "eventId": "…", "occurredAt": "…", "orderId": 12, "customerId": "<sub keycloak>",
  "amount": 46.80, "currency": "EUR",
  "items": [ { "productId": 7, "productName": "Keyboard", "unitPrice": 23.40, "quantity": 2 } ] }

// order.confirmed
{ "eventId": "…", "occurredAt": "…", "orderId": 12, "customerId": "…",
  "shippingAddress": { "recipientName": "…", "addressLine": "…", "city": "…", "postalCode": "…", "countryCode": "IT" },
  "items": [ … ] }

// order.cancelled
{ "eventId": "…", "occurredAt": "…", "orderId": 12, "customerId": "…", "reason": "…" }

// payment.completed
{ "eventId": "…", "occurredAt": "…", "orderId": 12, "paymentId": 5, "amount": 46.80, "currency": "EUR", "transactionReference": "SIM-…" }

// payment.failed
{ "eventId": "…", "occurredAt": "…", "orderId": 12, "paymentId": 5, "reason": "…" }

// payment.refunded
{ "eventId": "…", "occurredAt": "…", "orderId": 12, "paymentId": 5, "amount": 46.80, "currency": "EUR", "refundReference": "SIM-REFUND-…" }

// shipping.updated — status: CREATED | SHIPPED | DELIVERED | CANCELLED
{ "eventId": "…", "occurredAt": "…", "orderId": 12, "shipmentId": 3, "status": "SHIPPED", "carrier": "SIMULATED", "trackingCode": "TRK-…" }
```

## Garanzie

### Nessun evento perso: Transactional Outbox

Un servizio non pubblica direttamente su RabbitMQ dentro la logica di dominio. Salva l'evento nella tabella `outbox_events` **nella stessa transazione** della modifica: per esempio l'ordine e il suo `ORDER_PLACED` vengono salvati insieme o non vengono salvati affatto.

`OutboxRelay` legge ogni secondo gli eventi non pubblicati e li invia. Segna un evento come pubblicato solo se valgono entrambe le condizioni:

- RabbitMQ conferma la ricezione (*publisher confirm*);
- almeno una coda ha ricevuto il messaggio (*mandatory* + *returns*).

Se il broker è giù, gli eventi restano in tabella e partono quando torna disponibile. Le righe già pubblicate vengono cancellate dopo 7 giorni. `SELECT … FOR UPDATE SKIP LOCKED` permette di avviare più istanze dello stesso servizio senza pubblicare due volte.

### Nessun effetto doppio: Idempotent Consumer

La consegna è *at-least-once*: lo stesso messaggio può arrivare più di una volta. Prima di elaborare un evento, il consumatore registra il suo `eventId` nella tabella `processed_events`, nella stessa transazione degli effetti. Un duplicato viene riconosciuto e ignorato.

### Errori: retry e dead-letter

Se l'elaborazione lancia un'eccezione, il messaggio viene ritentato 3 volte con backoff (1 s, 2 s, 4 s). Se fallisce ancora, finisce in `<coda>.dlq`, dove resta visibile nella console RabbitMQ (`http://localhost:15672`) per essere analizzato e, se serve, rimesso in coda.

## Casi limite gestiti

| Situazione | Comportamento |
|---|---|
| Il cliente annulla mentre il pagamento è in corso | Se il pagamento è già riuscito viene **rimborsato**; se `ORDER_CANCELLED` arriva prima di `ORDER_PLACED`, il payment-service salva un segnaposto `CANCELLED` e non addebita mai. |
| `PAYMENT_COMPLETED` arriva per un ordine già annullato | L'order-service lo ignora (il rimborso è già gestito dal payment-service). |
| Ordine annullato prima della conferma | Lo shipping-service salva un segnaposto `CANCELLED` e non crea la spedizione se poi arriva `ORDER_CONFIRMED`. |
| Ordine annullato dopo che il pacco è partito | Non succede tramite API: l'annullamento è permesso solo in `PLACED` o `CONFIRMED`. In caso di corsa, lo shipping-service registra un warning per una gestione manuale. |
| Due richieste modificano lo stesso ordine | Optimistic locking (`@Version`): una delle due fallisce e viene ritentata (eventi) o riceve `409` (API). |
