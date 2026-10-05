package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.Profile;
import br.edu.ifrn.wonder.core.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void shouldReturnExistingProfileWhenFoundByKeycloakUserId() {
        UUID keycloakUserId = UUID.randomUUID();
        Profile existingProfile = new Profile(keycloakUserId);

        when(profileRepository.findByKeycloakUserId(keycloakUserId))
                .thenReturn(Optional.of(existingProfile));

        Profile result = profileService.findOrCreateByKeycloakUserId(keycloakUserId);

        assertThat(result).isSameAs(existingProfile);
        verify(profileRepository, never()).saveAndFlush(any(Profile.class));
    }

    @Test
    void shouldCreateProfileWhenNotFoundByKeycloakUserId() {
        UUID keycloakUserId = UUID.randomUUID();
        Profile savedProfile = new Profile(keycloakUserId);

        when(profileRepository.findByKeycloakUserId(keycloakUserId))
                .thenReturn(Optional.empty());
        when(profileRepository.saveAndFlush(any(Profile.class)))
                .thenAnswer(invocation -> {
                    Profile profileToSave = invocation.getArgument(0, Profile.class);
                    assertThat(profileToSave.getKeycloakUserId()).isEqualTo(keycloakUserId);
                    return savedProfile;
                });

        Profile result = profileService.findOrCreateByKeycloakUserId(keycloakUserId);

        assertThat(result).isSameAs(savedProfile);
        verify(profileRepository).saveAndFlush(any(Profile.class));
    }

    @Test
    void shouldReturnExistingProfileWhenCreateLosesConcurrentRace() {
        UUID keycloakUserId = UUID.randomUUID();
        Profile existingProfile = new Profile(keycloakUserId);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicated keycloak id");

        when(profileRepository.findByKeycloakUserId(keycloakUserId))
                .thenReturn(Optional.empty(), Optional.of(existingProfile));
        when(profileRepository.saveAndFlush(any(Profile.class)))
                .thenAnswer(invocation -> {
                    throw violation;
                });

        Profile result = profileService.findOrCreateByKeycloakUserId(keycloakUserId);

        assertThat(result).isSameAs(existingProfile);
    }

    @Test
    void shouldRethrowDataIntegrityViolationWhenProfileStillCannotBeFound() {
        UUID keycloakUserId = UUID.randomUUID();
        DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicated keycloak id");

        when(profileRepository.findByKeycloakUserId(keycloakUserId))
                .thenReturn(Optional.empty());
        when(profileRepository.saveAndFlush(any(Profile.class)))
                .thenAnswer(invocation -> {
                    throw violation;
                });

        assertThatThrownBy(() -> profileService.findOrCreateByKeycloakUserId(keycloakUserId))
                .isSameAs(violation);
    }
}
