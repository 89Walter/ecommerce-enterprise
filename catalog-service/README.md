# Catalog Service

## Swagger UI

Avvia il Catalog Service e apri la documentazione interattiva delle API:

```text
http://localhost:8081/api/v1/catalog/swagger-ui/index.html
```

La porta `8081` e il context path `/api/v1/catalog` sono i valori predefiniti configurati in `src/main/resources/application.yaml`. Se li modifichi, aggiorna di conseguenza l'URL.

Le operazioni di lettura sono pubbliche. Per provare le operazioni di scrittura da Swagger UI, clicca il lucchetto dell'operazione e inserisci un token con ruolo `ADMIN`. Per configurare Keycloak e ottenere un token, consulta [KEYCLOAK.md](KEYCLOAK.md).
