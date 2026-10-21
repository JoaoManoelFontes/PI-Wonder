package br.edu.ifrn.wonder.core.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusServico;

public interface PrestadorRepository extends JpaRepository<Prestador, Long> {

    @EntityGraph(attributePaths = "perfilUsuario")
    List<Prestador> findByAtivoTrueAndStatusOrderByNomeEstabAsc(StatusPrestador status);

    @EntityGraph(attributePaths = "perfilUsuario")
    @Query("""
            SELECT prestador FROM Prestador prestador
            WHERE prestador.ativo = true
              AND prestador.status = :statusPrestador
              AND LOWER(prestador.nomeEstab) LIKE LOWER(CONCAT('%', :nome, '%'))
              AND (:categoriaId IS NULL OR EXISTS (
                  SELECT servico.id FROM Servico servico
                  JOIN servico.categoria categoria
                  WHERE servico.prestador = prestador
                    AND categoria.id = :categoriaId
                    AND categoria.status = :statusCategoria
                    AND servico.status = :statusServico
              ))
            ORDER BY prestador.nomeEstab ASC
            """)
    List<Prestador> buscarPublicosComFiltros(
            @Param("nome") String nome,
            @Param("categoriaId") Long categoriaId,
            @Param("statusPrestador") StatusPrestador statusPrestador,
            @Param("statusCategoria") StatusCategoria statusCategoria,
            @Param("statusServico") StatusServico statusServico);

    @EntityGraph(attributePaths = "perfilUsuario")
    Optional<Prestador> findByIdAndAtivoTrueAndStatus(Long id, StatusPrestador status);

    @EntityGraph(attributePaths = "perfilUsuario")
    Optional<Prestador> findById(Long id);

    Optional<Prestador> findByPerfilUsuarioId(UUID perfilUsuarioId);

    @EntityGraph(attributePaths = "perfilUsuario")
    Optional<Prestador> findByPerfilUsuarioKeycloakId(UUID keycloakId);

    @EntityGraph(attributePaths = "perfilUsuario")
    Optional<Prestador> findByIdAndStatusAndAtivoTrue(Long id, StatusPrestador status);

    @EntityGraph(attributePaths = "perfilUsuario")
    List<Prestador> findByStatusOrderByEnviadoEmAsc(StatusPrestador status);
}
