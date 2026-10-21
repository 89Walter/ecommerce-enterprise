package com.ecommerce.notification.notification;

/**
 * L'evento di pagamento o spedizione è arrivato prima di quello dell'ordine con l'email del cliente
 * (le code sono indipendenti). L'eccezione fa ritentare il messaggio; se l'email non arriva mai,
 * dopo i retry il messaggio finisce nella dead-letter queue.
 */
public class ContactNotYetKnownException extends RuntimeException {

    public ContactNotYetKnownException(Long orderId) {
        super("Contatto del cliente per l'ordine " + orderId + " non ancora disponibile.");
    }
}
