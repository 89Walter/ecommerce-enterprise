package com.ecommerce.shipping.shipment;

public enum ShipmentStatus {
    /** Spedizione creata, in preparazione nel magazzino. */
    CREATED,
    /** Pacco consegnato al corriere. */
    SHIPPED,
    /** Pacco consegnato al cliente. */
    DELIVERED,
    /** Spedizione annullata insieme all'ordine. */
    CANCELLED
}
