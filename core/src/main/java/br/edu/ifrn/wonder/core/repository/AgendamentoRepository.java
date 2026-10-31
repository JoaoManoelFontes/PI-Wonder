package br.edu.ifrn.wonder.core.repository;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT agendamento FROM Agendamento agendamento WHERE agendamento.id = :id AND agendamento.prestador.id = :prestadorId")
    Optional<Agendamento> buscarPorIdEPrestadorComBloqueio(@Param("id") Long id,
            @Param("prestadorId") Long prestadorId);

    boolean existsByServicoIdAndStatusIn(Long servicoId, Collection<StatusAgendamento> status);

    boolean existsByPrestadorIdAndStatusIn(Long prestadorId, Collection<StatusAgendamento> status);

    boolean existsByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusIn(
            Long prestadorId,
            LocalDateTime fim,
            LocalDateTime inicio,
            Collection<StatusAgendamento> status);

    @EntityGraph(attributePaths = {"cliente", "prestador", "servico"})
    List<Agendamento> findAllByOrderByInicioDesc();

    @EntityGraph(attributePaths = {"cliente", "prestador", "servico"})
    List<Agendamento> findByInicioGreaterThanEqualAndInicioLessThanOrderByInicioDesc(
            LocalDateTime inicio,
            LocalDateTime fim);

    @EntityGraph(attributePaths = {"cliente", "prestador", "servico"})
    List<Agendamento> findByClienteKeycloakIdOrderByInicioDesc(UUID keycloakId);

    @EntityGraph(attributePaths = {"cliente", "prestador", "servico"})
    List<Agendamento> findByClienteKeycloakIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioDesc(
            UUID keycloakId,
            LocalDateTime inicio,
            LocalDateTime fim);

    @EntityGraph(attributePaths = {"cliente", "prestador", "servico"})
    List<Agendamento> findByPrestadorPerfilUsuarioKeycloakIdOrderByInicioDesc(UUID keycloakId);

    @EntityGraph(attributePaths = {"cliente", "prestador", "servico"})
    List<Agendamento> findByPrestadorPerfilUsuarioKeycloakIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioDesc(
            UUID keycloakId,
            LocalDateTime inicio,
            LocalDateTime fim);

    List<Agendamento> findByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusNotOrderByInicioAsc(
            Long prestadorId,
            LocalDateTime fim,
            LocalDateTime inicio,
            StatusAgendamento status);
}
