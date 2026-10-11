-- Data del rimborso, registrata quando arriva l'evento payment.refunded per un ordine annullato
ALTER TABLE orders ADD COLUMN refunded_at DATETIME(6) NULL AFTER cancelled_at;
