package br.edu.ifrn.wonder.core.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifrn.wonder.core.domain.Prestador;

public interface PrestadorRepository extends JpaRepository<Prestador, Long> {

    Optional<Prestador> findByPerfilUsuarioId(UUID perfilUsuarioId);
}
