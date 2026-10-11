package com.ecommerce.user.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
@Transactional
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final TransactionOperations transactionOperations;

    /**
     * Restituisce il profilo dell'utente autenticato, creandolo al primo accesso con i dati del token.
     * Se due richieste concorrenti lo creano insieme, la seconda trova la chiave già presente e lo rilegge.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserProfileResponse getOrCreate(Jwt jwt) {
        try {
            return transactionOperations.execute(status -> UserProfileResponse.from(loadOrCreate(jwt)));
        } catch (DataIntegrityViolationException concurrentCreation) {
            return transactionOperations.execute(status -> UserProfileResponse.from(loadOrCreate(jwt)));
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserProfileResponse update(Jwt jwt, UpdateProfileRequest request) {
        getOrCreate(jwt);
        return transactionOperations.execute(status -> {
            UserProfile profile = userProfileRepository.findById(jwt.getSubject())
                    .orElseThrow(() -> new UserProfileNotFoundException(jwt.getSubject()));
            profile.update(request);
            return UserProfileResponse.from(userProfileRepository.saveAndFlush(profile));
        });
    }

    @Transactional(readOnly = true)
    public UserProfileResponse findById(String id) {
        return userProfileRepository.findById(id)
                .map(UserProfileResponse::from)
                .orElseThrow(() -> new UserProfileNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<UserProfileResponse> findAll(String email, Pageable pageable) {
        Page<UserProfile> profiles = email == null || email.isBlank()
                ? userProfileRepository.findAll(pageable)
                : userProfileRepository.findByEmailContainingIgnoreCase(email.trim(), pageable);
        return profiles.map(UserProfileResponse::from);
    }

    private UserProfile loadOrCreate(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return userProfileRepository.findById(jwt.getSubject())
                .map(profile -> profile.syncEmail(email) ? userProfileRepository.saveAndFlush(profile) : profile)
                .orElseGet(() -> userProfileRepository.saveAndFlush(UserProfile.fromIdentity(
                        jwt.getSubject(),
                        email,
                        jwt.getClaimAsString("given_name"),
                        jwt.getClaimAsString("family_name")
                )));
    }
}
