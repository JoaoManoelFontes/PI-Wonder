package br.edu.ifrn.wonder.core.repository;

import br.edu.ifrn.wonder.core.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
}
