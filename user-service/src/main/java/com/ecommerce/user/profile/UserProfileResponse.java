package com.ecommerce.user.profile;

import java.time.LocalDateTime;

public record UserProfileResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Address defaultShippingAddress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record Address(String recipientName, String addressLine, String city, String postalCode, String countryCode) {}

    static UserProfileResponse from(UserProfile profile) {
        Address address = profile.getDefaultAddressLine() == null ? null : new Address(
                profile.getDefaultRecipientName(),
                profile.getDefaultAddressLine(),
                profile.getDefaultCity(),
                profile.getDefaultPostalCode(),
                profile.getDefaultCountryCode()
        );
        return new UserProfileResponse(
                profile.getId(),
                profile.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getPhone(),
                address,
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
