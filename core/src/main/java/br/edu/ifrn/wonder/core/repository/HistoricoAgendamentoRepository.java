package br.edu.ifrn.wonder.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifrn.wonder.core.domain.HistoricoAgendamento;

public interface HistoricoAgendamentoRepository extends JpaRepository<HistoricoAgendamento, Long> {

    List<HistoricoAgendamento> findByAgendamentoIdOrderByIdAsc(Long agendamentoId);
}
