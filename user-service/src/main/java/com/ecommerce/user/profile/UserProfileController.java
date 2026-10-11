package com.ecommerce.user.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Utenti", description = "Profilo del cliente (i dati di accesso restano in Keycloak).")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(
            summary = "Profilo dell'utente autenticato",
            description = "Al primo accesso il profilo viene creato con email e nome presenti nel token.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userProfileService.getOrCreate(jwt);
    }

    @Operation(summary = "Aggiorna il profilo dell'utente autenticato", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/me")
    public UserProfileResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return userProfileService.update(jwt, request);
    }

    @Operation(summary = "Elenca i profili", description = "Riservato agli amministratori.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/admin")
    public PagedModel<UserProfileResponse> findAll(
            @Parameter(description = "Filtro opzionale sull'email.") @RequestParam(required = false) String email,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return new PagedModel<>(userProfileService.findAll(email, pageable));
    }

    @Operation(summary = "Recupera un profilo", description = "Riservato agli amministratori.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/admin/{id}")
    public UserProfileResponse findById(@PathVariable String id) {
        return userProfileService.findById(id);
    }
}
