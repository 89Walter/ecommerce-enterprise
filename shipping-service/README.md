# Shipping Service

Gestisce le spedizioni. Porta `8085`, base path `/api/v1/shipments`, database `shipping_db`.

| Evento ricevuto | Effetto |
|---|---|
| `order.confirmed` | crea la spedizione in stato `CREATED`, con codice di tracking e una copia dell'indirizzo |
| `order.cancelled` | annulla la spedizione se il pacco non è ancora partito |

Ogni cambio di stato pubblica `shipping.updated`. L'order-service lo usa per portare l'ordine in `SHIPPED` e poi in `DELIVERED`.

```
CREATED ──► SHIPPED ──► DELIVERED
   └──► CANCELLED
```

## API

| Metodo | Endpoint | Accesso |
|---|---|---|
| `GET` | `/api/v1/shipments/orders/{orderId}` | proprietario dell'ordine o ADMIN |
| `GET` | `/api/v1/shipments/admin?status=` | ADMIN |
| `PATCH` | `/api/v1/shipments/admin/{id}/status` | ADMIN; body `{"status": "SHIPPED"}`, poi `DELIVERED` |

Il corriere è simulato (`SHIPPING_DEFAULT_CARRIER`, predefinito `SIMULATED`).
