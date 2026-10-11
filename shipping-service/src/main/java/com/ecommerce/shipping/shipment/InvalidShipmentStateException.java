package com.ecommerce.shipping.shipment;

public class InvalidShipmentStateException extends RuntimeException {

    public InvalidShipmentStateException(ShipmentStatus current, ShipmentStatus target) {
        super("Transizione spedizione non consentita: " + current + " -> " + target + ".");
    }
}
