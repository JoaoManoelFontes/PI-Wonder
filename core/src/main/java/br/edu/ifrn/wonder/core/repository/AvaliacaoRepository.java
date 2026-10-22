package br.edu.ifrn.wonder.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifrn.wonder.core.domain.Avaliacao;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    @EntityGraph(attributePaths = {"agendamento", "agendamento.prestador"})
    List<Avaliacao> findByAgendamentoPrestadorIdOrderByIdAsc(Long prestadorId);
}
