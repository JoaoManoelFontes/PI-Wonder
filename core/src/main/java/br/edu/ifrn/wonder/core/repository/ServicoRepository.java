package br.edu.ifrn.wonder.core.repository;

import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicoRepository extends JpaRepository<Servico, Long> {

    List<Servico> findByPrestadorIdAndStatusOrderByNomeAsc(Long prestadorId, StatusServico status);

    Optional<Servico> findByIdAndPrestadorId(Long id, Long prestadorId);
}
