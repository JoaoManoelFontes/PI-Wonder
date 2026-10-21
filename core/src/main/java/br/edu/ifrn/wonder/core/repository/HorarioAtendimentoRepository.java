package br.edu.ifrn.wonder.core.repository;

import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalTime;

public interface HorarioAtendimentoRepository extends JpaRepository<HorarioAtendimento, Long> {

    boolean existsByPrestadorIdAndDiaSemanaAndHoraInicioLessThanAndHoraFimGreaterThan(
            Long prestadorId, Integer diaSemana, LocalTime horaFim, LocalTime horaInicio);

    List<HorarioAtendimento> findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(Long prestadorId);

    Optional<HorarioAtendimento> findByIdAndPrestadorId(Long id, Long prestadorId);
}
