# Catalog Service

Gestisce i prodotti: creazione, modifica, cancellazione, ricerca testuale paginata. Porta `8081`, base path `/api/v1/catalog`, database `catalog_db`.

## Avvio

Dalla root del progetto avvia l'infrastruttura (`docker compose up -d`), poi avvia il servizio da IntelliJ oppure con:

```bash
mvn -f catalog-service/pom.xml spring-boot:run
```

Le credenziali sono lette dal `.env` della root. Vedi il [README principale](../README.md).

## API

| Metodo | Endpoint | Accesso |
|---|---|---|
| `GET` | `/products?search=&page=&size=&sort=` | pubblico |
| `GET` | `/products/{id}` | pubblico |
| `POST` | `/products` | ADMIN |
| `PUT` | `/products/{id}` | ADMIN |
| `DELETE` | `/products/{id}` | ADMIN |

Il prezzo deve essere positivo, con al massimo 8 cifre intere e 2 decimali, come la colonna `DECIMAL(10,2)`. Le pagine hanno al massimo 100 elementi.

Swagger UI: `http://localhost:8081/api/v1/catalog/swagger-ui/index.html`. Per le operazioni protette clicca il lucchetto e inserisci un token con ruolo `ADMIN` (vedi [docs/keycloak.md](../docs/keycloak.md)).

## Cache Redis

`GET /products/{id}` è la lettura più frequente: la usa anche l'order-service per ogni riga d'ordine. Per questo i risultati vengono messi in cache su Redis:

- chiave `catalog::products::<id>`;
- durata `CATALOG_PRODUCT_CACHE_TTL`, predefinita 10 minuti;
- `PUT` aggiorna la cache, `DELETE` la invalida;
- valori in JSON tipizzato, senza nomi di classi Java;
- se Redis non è raggiungibile, l'errore finisce nei log e la lettura va sul database: la cache non è mai un punto di guasto.

Le liste paginate non sono in cache: cambiano con ogni filtro e sarebbe difficile tenerle coerenti.
