# Payment Service

Esegue gli addebiti degli ordini e i rimborsi. Porta `8084`, base path `/api/v1/payments`, database `payment_db`.

Non ha endpoint di scrittura: i pagamenti nascono e cambiano stato **solo** tramite gli eventi.

| Evento ricevuto | Effetto | Evento pubblicato |
|---|---|---|
| `order.placed` | addebito | `payment.completed` oppure `payment.failed` |
| `order.cancelled` | rimborso, se l'ordine era già stato addebitato | `payment.refunded` |

Stati del pagamento: `COMPLETED`, `FAILED`, `REFUNDED` e `CANCELLED`. `CANCELLED` indica un ordine annullato prima che l'addebito partisse. È ammesso un solo pagamento per ordine (vincolo `UNIQUE`).

## Gateway di pagamento

`PaymentGateway` è un'interfaccia: le implementazioni sono intercambiabili, per esempio Stripe o PayPal. Quella attuale, `SimulatedPaymentGateway`:

- approva gli importi fino a `PAYMENT_MAX_APPROVED_AMOUNT` (predefinito 5000);
- rifiuta quelli superiori, così puoi provare anche il percorso di errore.

Per integrare un fornitore reale, aggiungi una nuova implementazione e rendila il bean attivo.

## API

| Metodo | Endpoint | Accesso |
|---|---|---|
| `GET` | `/api/v1/payments/orders/{orderId}` | proprietario dell'ordine o ADMIN |
| `GET` | `/api/v1/payments/admin?status=` | ADMIN |
