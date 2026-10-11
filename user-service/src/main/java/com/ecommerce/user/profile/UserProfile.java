package com.ecommerce.user.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Dati del cliente che non riguardano l'autenticazione. Password, login e ruoli restano in Keycloak;
 * il collegamento è l'ID dell'utente Keycloak (claim "sub" del token), usato come chiave primaria.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @Column(length = 255)
    private String id;

    @Column(length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 30)
    private String phone;

    @Column(name = "default_recipient_name", length = 120)
    private String defaultRecipientName;

    @Column(name = "default_address_line", length = 255)
    private String defaultAddressLine;

    @Column(name = "default_city", length = 120)
    private String defaultCity;

    @Column(name = "default_postal_code", length = 20)
    private String defaultPostalCode;

    @Column(name = "default_country_code", length = 2)
    private String defaultCountryCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private long version;

    static UserProfile fromIdentity(String id, String email, String firstName, String lastName) {
        UserProfile profile = new UserProfile();
        profile.id = id;
        profile.email = email;
        profile.firstName = firstName;
        profile.lastName = lastName;
        return profile;
    }

    /** L'email è gestita da Keycloak: viene riallineata a ogni accesso se cambia. */
    boolean syncEmail(String email) {
        if (email == null || email.equals(this.email)) {
            return false;
        }
        this.email = email;
        return true;
    }

    void update(UpdateProfileRequest request) {
        this.firstName = trimToNull(request.firstName());
        this.lastName = trimToNull(request.lastName());
        this.phone = trimToNull(request.phone());
        AddressRequest address = request.defaultShippingAddress();
        this.defaultRecipientName = address == null ? null : address.recipientName().trim();
        this.defaultAddressLine = address == null ? null : address.addressLine().trim();
        this.defaultCity = address == null ? null : address.city().trim();
        this.defaultPostalCode = address == null ? null : address.postalCode().trim();
        this.defaultCountryCode = address == null ? null : address.countryCode().trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
