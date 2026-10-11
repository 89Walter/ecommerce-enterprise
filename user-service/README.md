# User Service

Profilo del cliente. Porta `8083`, base path `/api/v1/users`, database `user_db`.

Login, password, registrazione e ruoli restano in **Keycloak**: questo servizio non li duplica. Conserva solo i dati che servono all'e-commerce:

- nome e cognome;
- telefono;
- indirizzo di spedizione predefinito.

La chiave è l'ID dell'utente Keycloak, cioè il claim `sub`: è lo stesso valore che l'order-service salva come `customerId`.

Il profilo viene creato in automatico al primo `GET /me`, con email e nome presi dal token. L'email viene poi riallineata a ogni accesso, perché è gestita da Keycloak.

## API

| Metodo | Endpoint | Accesso |
|---|---|---|
| `GET` | `/api/v1/users/me` | utente |
| `PUT` | `/api/v1/users/me` | utente |
| `GET` | `/api/v1/users/admin?email=` | ADMIN |
| `GET` | `/api/v1/users/admin/{id}` | ADMIN |

Esempio di `PUT /me`. Senza `defaultShippingAddress`, l'indirizzo predefinito viene rimosso.

```json
{
  "firstName": "Ada",
  "lastName": "Lovelace",
  "phone": "+39 333 1234567",
  "defaultShippingAddress": {
    "recipientName": "Ada Lovelace",
    "addressLine": "Via Roma 1",
    "city": "Torino",
    "postalCode": "10100",
    "countryCode": "IT"
  }
}
```
