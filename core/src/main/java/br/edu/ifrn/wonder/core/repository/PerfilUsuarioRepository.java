package br.edu.ifrn.wonder.core.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;

public interface PerfilUsuarioRepository extends JpaRepository<PerfilUsuario, UUID> {

    Optional<PerfilUsuario> findByKeycloakId(UUID keycloakId);
}
