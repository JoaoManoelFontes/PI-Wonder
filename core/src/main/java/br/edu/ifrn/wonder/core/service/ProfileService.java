package br.edu.ifrn.wonder.core.service;

import br.edu.ifrn.wonder.core.domain.Profile;
import br.edu.ifrn.wonder.core.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;

    public Profile findOrCreateByKeycloakUserId(UUID keycloakUserId) {
        return profileRepository.findByKeycloakUserId(keycloakUserId)
                .orElseGet(() -> createProfile(keycloakUserId));
    }

    private Profile createProfile(UUID keycloakUserId) {
        try {
            return profileRepository.saveAndFlush(new Profile(keycloakUserId));
        } catch (DataIntegrityViolationException exception) {
            return profileRepository.findByKeycloakUserId(keycloakUserId)
                    .orElseThrow(() -> exception);
        }
    }
}
