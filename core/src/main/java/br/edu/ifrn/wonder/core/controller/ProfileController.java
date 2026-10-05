package br.edu.ifrn.wonder.core.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.domain.Profile;
import br.edu.ifrn.wonder.core.security.AuthenticatedUserProvider;
import br.edu.ifrn.wonder.core.service.ProfileService;

@RestController
public class ProfileController {

    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final ProfileService profileService;

    public ProfileController(AuthenticatedUserProvider authenticatedUserProvider, ProfileService profileService) {
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.profileService = profileService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    ProfileResponse me() {
        Profile profile = profileService.findOrCreateByKeycloakUserId(authenticatedUserProvider.getUserUuid());

        return ProfileResponse.from(profile);
    }

    private record ProfileResponse(
            UUID id,
            UUID keycloakUserId,
            String photoUrl,
            String phone
    ) {
        private static ProfileResponse from(Profile profile) {
            return new ProfileResponse(
                    profile.getId(),
                    profile.getKeycloakUserId(),
                    profile.getPhotoUrl(),
                    profile.getPhone()
            );
        }
    }
}
