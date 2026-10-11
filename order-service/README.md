# Order Service

Crea e gestisce gli ordini. Porta `8082`, base path `/api/v1/orders`, database `order_db`. Il cliente è identificato dal claim `sub` del token, mai dal body della richiesta.

## API

| Metodo | Endpoint | Accesso |
|---|---|---|
| `POST` | `/api/v1/orders` | utente |
| `GET` | `/api/v1/orders` | utente (solo i propri, paginati) |
| `GET` | `/api/v1/orders/{id}` | utente (solo i propri, altrimenti 404) |
| `POST` | `/api/v1/orders/{id}/cancel` | utente, con motivazione |
| `GET` | `/api/v1/orders/admin?status=` | ADMIN |

Esempio di creazione:

```json
{
  "items": [
    { "productId": 12, "quantity": 2 },
    { "productId": 18, "quantity": 1 }
  ],
  "shippingAddress": {
    "recipientName": "Ada Lovelace",
    "addressLine": "Via Roma 1",
    "city": "Torino",
    "postalCode": "10100",
    "countryCode": "IT"
  }
}
```

Durante la creazione dell'ordine il servizio:

- legge i prodotti dal Catalog, con timeout di 2 s per la connessione e 3 s per la risposta;
- unisce le righe dello stesso prodotto, fino a un massimo di 99 pezzi;
- salva nome e prezzo come **snapshot**, così un cambio di prezzo successivo non altera gli ordini esistenti.

Le chiamate al Catalog avvengono **fuori** dalla transazione. Il salvataggio dell'ordine e dell'evento `ORDER_PLACED` avviene invece in un'unica transazione (outbox).

## Ciclo di vita

```
PLACED ──pagamento ok──► CONFIRMED ──spedito──► SHIPPED ──consegnato──► DELIVERED
   │                         │
   └──pagamento rifiutato──► CANCELLED ◄──annullamento del cliente
```

Gli stati successivi a `PLACED` **non si cambiano a mano**. Li fanno avanzare gli eventi del payment-service e dello shipping-service (vedi `OrderWorkflowService` e [docs/events.md](../docs/events.md)). Il vecchio endpoint `PATCH /orders/{id}/status` è stato rimosso: un amministratore fa avanzare la spedizione dallo shipping-service, e l'ordine si aggiorna di conseguenza.

## Errori

| Codice | Quando |
|---|---|
| 400 | Validazione |
| 404 | Ordine inesistente o di un altro cliente |
| 409 | Transizione non consentita o modifica concorrente |
| 422 | Prodotto inesistente o ordine non valido |
| 502 | Catalog non raggiungibile |
