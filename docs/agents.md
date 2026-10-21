# Agenti AI

Gli agenti usano **Claude** (API di Anthropic, SDK Java ufficiale `com.anthropic:anthropic-java`). La logica sta tutta nel backend: la chiave API non arriva mai al browser, e il frontend mostra solo chat e pulsanti.

## Configurazione

Nel `.env` della root:

| Variabile | Default | A cosa serve |
|---|---|---|
| `ANTHROPIC_API_KEY` | vuota | Chiave da [console.anthropic.com](https://console.anthropic.com) → API Keys. Se è vuota, il resto del negozio funziona e le funzioni AI rispondono `503` "non configurato". |
| `ASSISTANT_MODEL` / `CATALOG_AI_MODEL` | `claude-opus-5-5` | Modello usato dall'assistente e dal generatore di descrizioni. |
| `ASSISTANT_EFFORT` / `CATALOG_AI_EFFORT` | `low` | Quanto ragiona il modello (`low`, `medium`, `high`, `xhigh`, `max`). Valori più alti rendono le risposte più lente e costose: `low` va bene per chat e testi brevi. |
| `GATEWAY_AI_RATE_LIMIT_PER_SECOND` / `_BURST` | `1` / `5` | Richieste AI per utente consentite dal gateway, in aggiunta al limite generale. |

Dopo aver inserito la chiave, riavvia i servizi interessati (`catalog-service` e `assistant-service`).

Ogni chiamata a Claude ha un costo, addebitato sull'account Anthropic della chiave. Per questo le rotte AI hanno un limite di richieste proprio nel gateway e l'assistente fa al massimo 8 giri di strumenti per domanda.

## Fase 1 (implementata)

### Generatore di schede prodotto — `catalog-service`

- **API**: `POST /api/v1/catalog/products/{id}/description-suggestion`, solo per ADMIN.
- **Che cos'è**: una singola chiamata a Claude senza strumenti, quindi più una funzione AI che un vero agente. Riceve nome, descrizione attuale e prezzo, e restituisce una proposta di 2-4 frasi.
- **Cosa non fa**: non salva nulla. L'amministratore legge la proposta e, se gli va bene, la salva con la normale modifica del prodotto.
- **Frontend**: Back-office → Prodotti → Modifica → **✨ Suggerisci descrizione con AI**.
- **Codice**: `catalog-service/.../ai/ProductDescriptionWriter.java`.

### Assistente clienti — `assistant-service` (porta 8086)

Un agente vero e proprio: Claude decide quali strumenti usare per rispondere. Fa due cose:

- **assistente ordini**: stato, pagamento, spedizione e tracking, motivo di un annullamento;
- **commesso virtuale**: ricerca e consiglio di prodotti, anche con un budget.

| API | Descrizione |
|---|---|
| `POST /api/v1/assistant/chat` | `{"message": "...", "conversationId": "..."}`. Alla prima domanda si omette `conversationId`; poi si rimanda quello ricevuto. |
| `GET /api/v1/assistant/status` | `{"available": true/false}`: dice se la chiave è configurata. |
| `DELETE /api/v1/assistant/conversations/{id}` | Dimentica una conversazione. |

Nel frontend la pagina è **Assistente** (`/assistant`), visibile agli utenti autenticati.

**Strumenti dell'agente** (`ShopTools`). Sono tutti di sola lettura e vengono eseguiti **con il token dell'utente**:

| Strumento | Chiama |
|---|---|
| `search_products` | `GET /catalog/products?search=…` |
| `get_product` | `GET /catalog/products/{id}` |
| `list_my_orders` | `GET /orders` |
| `get_order` | `GET /orders/{id}` |
| `get_order_payment` | `GET /payments/orders/{id}` |
| `get_order_shipment` | `GET /shipments/orders/{id}` |
| `get_my_profile` | `GET /users/me` |

**Il ciclo** (`AssistantChatService`):

```
domanda → Claude → chiede uno strumento? → lo eseguiamo → risultato a Claude → … → risposta finale
```

## Scelte di sicurezza e affidabilità

- **Nessun permesso proprio.** L'agente usa il token dell'utente, quindi vede solo ciò che l'utente vedrebbe dal sito. Gli ordini di un altro cliente risultano "non trovati", come nelle API.
- **Sola lettura.** L'assistente non può creare o annullare ordini: spiega come farlo dal sito. Se in futuro serviranno azioni (fase 2, assistente all'acquisto), andranno sempre confermate dall'utente.
- **Cronologia sul server.** Le conversazioni stanno su Redis (`assistant:conversation:<utente>:<id>`), scadono dopo 2 ore di inattività e tengono gli ultimi 20 messaggi. Il browser manda solo il nuovo messaggio, quindi non può inserire finte risposte dell'assistente.
- **I dati degli strumenti sono dati, non istruzioni.** Il prompt di sistema lo dice esplicitamente: una descrizione di prodotto che contiene "ignora le istruzioni" viene trattata come testo.
- **Rifiuti e fallback.** Le richieste abilitano il fallback lato server (`fallbacks: "default"`): se il modello rifiuta per un falso positivo dei filtri di sicurezza, l'API riprova con un altro modello. Se il rifiuto resta, il cliente riceve un messaggio cortese.
- **Prompt cache.** Istruzioni e strumenti sono fissi e la cronologia cresce solo in coda, quindi le richieste successive riusano la cache (meno costo e meno latenza).
- **Timeout.** Il gateway concede 140 s all'assistente e 70 s al generatore di descrizioni; le altre rotte restano a 5 s.
- **Errori.** Chiave mancante → 503; chiave non valida → 503 (dettaglio solo nei log); troppe richieste → 429; Anthropic non raggiungibile → 502/503.

## Keycloak

Il nuovo servizio richiede l'audience `assistant-service` nel token, aggiunta dal client scope `assistant-audience`. Su un Keycloak già avviato lo crea e lo assegna ai client lo script:

```bash
bash infra/keycloak/create-dev-users.sh
```

## Fase 2 (da fare)

| Agente | Dove | Note |
|---|---|---|
| Assistente all'acquisto | `assistant-service` | Prepara il carrello o l'ordine; la creazione richiede la conferma esplicita del cliente. |
| Antifrode | nuovo `fraud-service` | Consuma `order.placed` e **segnala** a un umano, senza bloccare da solo. |
| Gestore problemi di spedizione | `shipping-service` | Spedizioni ferme, annullamenti dopo la partenza. |
| Analista vendite | nuovo servizio di reportistica | Richiede dati aggregati da più servizi. |
| Analizzatore dead-letter queue | strumento di sviluppo | Script, non un servizio sempre acceso. |
