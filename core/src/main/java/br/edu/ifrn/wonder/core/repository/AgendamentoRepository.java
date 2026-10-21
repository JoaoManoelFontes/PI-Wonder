package br.edu.ifrn.wonder.core.repository;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    boolean existsByServicoIdAndStatusIn(Long servicoId, Collection<StatusAgendamento> status);

    List<Agendamento> findByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusNotOrderByInicioAsc(
            Long prestadorId,
            LocalDateTime fim,
            LocalDateTime inicio,
            StatusAgendamento status);
}
