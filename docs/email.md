# Email

Il sistema invia due tipi di email:

| Chi le invia | Quali | Come |
|---|---|---|
| **Keycloak** | "Password dimenticata?", verifica dell'email dopo la registrazione | SMTP configurato nel realm (`smtpServer`) |
| **notification-service** | aggiornamenti sugli ordini | ascolta gli eventi su RabbitMQ |

## In sviluppo: Mailpit

Tutte le email finiscono in **Mailpit**, una casella di posta finta: **nessuna email esce davvero**. Si leggono su http://localhost:8025, in versione HTML, testo semplice e sorgente.

- SMTP per i container: `mailpit:1025`.
- SMTP per i servizi avviati da IntelliJ: `localhost:1025`.

## Email degli ordini (notification-service)

| Evento | Email al cliente |
|---|---|
| `order.confirmed` | **Ordine #N confermato**: righe, totale, indirizzo di spedizione |
| `order.cancelled` | **Ordine #N annullato**, con il motivo (anche per un pagamento rifiutato) |
| `payment.refunded` | **Rimborso dell'ordine #N**, con l'importo |
| `shipping.updated` → `SHIPPED` | **Il tuo ordine #N è stato spedito**, con corriere e codice di tracking |
| `shipping.updated` → `DELIVERED` | **Ordine #N consegnato** |

Ogni email contiene il link all'ordine sul sito (`SHOP_URL/orders/N`).

**Da dove arriva l'indirizzo.** L'order-service legge email e nome dal token del cliente quando crea l'ordine, li salva sull'ordine (colonne `customer_email` e `customer_name`) e li include negli eventi `order.*`. Il notification-service si tiene una piccola rubrica `order_contacts` (ordine → email) per gli eventi di pagamento e spedizione, che contengono solo il numero d'ordine.

**Affidabilità:**

- Ogni evento è elaborato una sola volta (tabella `processed_events`).
- Il registro `sent_notifications` ha un vincolo `UNIQUE (order_id, type)`: la stessa comunicazione non parte mai due volte per lo stesso ordine.
- Se il server SMTP non risponde, la transazione viene annullata e RabbitMQ ritenta 3 volte; dopo, il messaggio va nella dead-letter queue `notification-service.<evento>.dlq`.
- Se un evento di spedizione arriva prima di quello con l'email (le code sono indipendenti), viene ritentato finché il contatto non è disponibile.
- Gli ordini creati prima di questa funzione non hanno l'email: nessuna notifica, solo un warning nei log.

**Sicurezza:** nomi, prodotti, indirizzi e motivi vengono "escapati" prima di finire nell'HTML, quindi un nome come `<script>` resta testo.

## Keycloak

Nel realm (`infra/keycloak/ecommerce-realm.json`) sono attivi:

- `resetPasswordAllowed`: compare il link **Forgot Password?** nella pagina di login;
- `verifyEmail`: chi si registra deve confermare l'email cliccando il link ricevuto. In sviluppo, il link si apre da Mailpit.

Su un Keycloak già avviato queste impostazioni le applica `bash infra/keycloak/create-dev-users.sh`.

## In produzione

Si sostituisce Mailpit con un servizio SMTP reale (Amazon SES, SendGrid, Brevo, il provider di posta del dominio…):

- **notification-service** (`.env`):

  ```
  MAIL_HOST=…
  MAIL_PORT=587
  MAIL_USERNAME=…
  MAIL_PASSWORD=…
  MAIL_SMTP_AUTH=true
  MAIL_SMTP_STARTTLS=true
  MAIL_FROM_ADDRESS=noreply@tuodominio.it
  MAIL_FROM_NAME=…
  SHOP_URL=https://tuodominio.it
  ```

- **Keycloak**: realm **ecommerce** → **Realm settings** → **Email**, con gli stessi dati.

Il dominio del mittente deve avere SPF e DKIM configurati, altrimenti le email finiscono nello spam.
