package com.ecommerce.user.profile;

public class UserProfileNotFoundException extends RuntimeException {

    public UserProfileNotFoundException(String id) {
        super("Profilo utente " + id + " non trovato.");
    }
}
