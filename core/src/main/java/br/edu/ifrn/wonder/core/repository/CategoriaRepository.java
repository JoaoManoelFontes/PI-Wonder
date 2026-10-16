package br.edu.ifrn.wonder.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findByStatusOrderByNomeAsc(StatusCategoria status);

    Optional<Categoria> findByIdAndStatus(Long id, StatusCategoria status);
}
