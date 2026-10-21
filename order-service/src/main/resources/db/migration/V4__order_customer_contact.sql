-- Email e nome del cliente al momento dell'ordine, presi dal token: servono alle notifiche email.
-- Nullable: gli ordini creati prima di questa migrazione non li hanno.
ALTER TABLE orders
    ADD COLUMN customer_email VARCHAR(255) NULL AFTER customer_id,
    ADD COLUMN customer_name VARCHAR(255) NULL AFTER customer_email;
