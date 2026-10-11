package com.ecommerce.shipping.shipment;

public class ShipmentNotFoundException extends RuntimeException {

    public ShipmentNotFoundException(String message) {
        super(message);
    }
}
