package br.edu.ifrn.wonder.core.repository;

import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HorarioAtendimentoRepository extends JpaRepository<HorarioAtendimento, Long> {

    List<HorarioAtendimento> findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(Long prestadorId);

    Optional<HorarioAtendimento> findByIdAndPrestadorId(Long id, Long prestadorId);
}
