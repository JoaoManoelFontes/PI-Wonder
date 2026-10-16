package br.edu.ifrn.wonder.core.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;

public interface PrestadorRepository extends JpaRepository<Prestador, Long> {

    @EntityGraph(attributePaths = "perfilUsuario")
    List<Prestador> findByAtivoTrueAndStatusOrderByNomeEstabAsc(StatusPrestador status);

    @EntityGraph(attributePaths = "perfilUsuario")
    Optional<Prestador> findByIdAndAtivoTrueAndStatus(Long id, StatusPrestador status);

    @EntityGraph(attributePaths = "perfilUsuario")
    Optional<Prestador> findById(Long id);

    Optional<Prestador> findByPerfilUsuarioId(UUID perfilUsuarioId);

    @EntityGraph(attributePaths = "perfilUsuario")
    List<Prestador> findByStatusOrderByEnviadoEmAsc(StatusPrestador status);
}
