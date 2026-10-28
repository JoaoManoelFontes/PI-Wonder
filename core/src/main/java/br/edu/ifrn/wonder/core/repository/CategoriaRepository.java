package br.edu.ifrn.wonder.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusServico;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findAllByOrderByNomeAsc();

    List<Categoria> findByStatusOrderByNomeAsc(StatusCategoria status);

    Optional<Categoria> findByIdAndStatus(Long id, StatusCategoria status);

    @Query("""
            SELECT DISTINCT categoria FROM Servico servico
            JOIN servico.categoria categoria
            WHERE servico.prestador.id = :prestadorId
              AND servico.status = :statusServico
              AND categoria.status = :statusCategoria
            ORDER BY categoria.nome ASC
            """)
    List<Categoria> buscarPorPrestador(
            @Param("prestadorId") Long prestadorId,
            @Param("statusCategoria") StatusCategoria statusCategoria,
            @Param("statusServico") StatusServico statusServico);
}
