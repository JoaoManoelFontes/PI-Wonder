package br.edu.ifrn.wonder.core.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.dto.CadastroAgendamentoRequest;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private static final List<StatusAgendamento> STATUS_QUE_BLOQUEIAM_AGENDA = List.of(
            StatusAgendamento.PENDENTE,
            StatusAgendamento.CONFIRMADO
    );

    private final PerfilUsuarioService perfilUsuarioService;
    private final PrestadorRepository prestadorRepository;
    private final ServicoRepository servicoRepository;
    private final HorarioAtendimentoRepository horarioAtendimentoRepository;
    private final AgendamentoRepository agendamentoRepository;

    @Transactional
    public Agendamento criar(UUID keycloakId, CadastroAgendamentoRequest request) {
        PerfilUsuario cliente = perfilUsuarioService.buscarOuCriarPorKeycloakId(keycloakId);
        Prestador prestador = buscarPrestadorAtivoComBloqueio(request.prestadorId());
        Servico servico = servicoRepository
                .findByIdAndPrestadorIdAndStatus(request.servicoId(), prestador.getId(), StatusServico.ATIVO)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Serviço não encontrado para este prestador"
                ));

        LocalDateTime fim = request.inicio().plusMinutes(servico.getDuracaoMin());
        validarHorarioDeAtendimento(prestador.getId(), servico.getDuracaoMin(), request.inicio(), fim);
        validarDisponibilidade(prestador.getId(), request.inicio(), fim);

        return agendamentoRepository.save(Agendamento.builder()
                .cliente(cliente)
                .prestador(prestador)
                .servico(servico)
                .inicio(request.inicio())
                .fim(fim)
                .status(StatusAgendamento.PENDENTE)
                .build());
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listar(
            UUID keycloakId,
            boolean administrador,
            boolean prestador,
            LocalDate data) {
        LocalDateTime inicio = data == null ? null : data.atStartOfDay();
        LocalDateTime fim = data == null ? null : inicio.plusDays(1);

        if (administrador) {
            return data == null
                    ? agendamentoRepository.findAllByOrderByInicioDesc()
                    : agendamentoRepository.findByInicioGreaterThanEqualAndInicioLessThanOrderByInicioDesc(inicio, fim);
        }

        if (prestador) {
            return data == null
                    ? agendamentoRepository.findByPrestadorPerfilUsuarioKeycloakIdOrderByInicioDesc(keycloakId)
                    : agendamentoRepository
                            .findByPrestadorPerfilUsuarioKeycloakIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioDesc(
                                    keycloakId,
                                    inicio,
                                    fim
                            );
        }

        return data == null
                ? agendamentoRepository.findByClienteKeycloakIdOrderByInicioDesc(keycloakId)
                : agendamentoRepository.findByClienteKeycloakIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioDesc(
                        keycloakId,
                        inicio,
                        fim
                );
    }

    private Prestador buscarPrestadorAtivoComBloqueio(Long prestadorId) {
        Prestador prestador = prestadorRepository.buscarPorIdComBloqueio(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));

        if (!prestador.isAtivo() || prestador.getStatus() != StatusPrestador.ATIVO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado");
        }
        return prestador;
    }

    private void validarHorarioDeAtendimento(
            Long prestadorId,
            int duracaoMin,
            LocalDateTime inicio,
            LocalDateTime fim) {
        boolean horarioValido = horarioAtendimentoRepository
                .findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestadorId)
                .stream()
                .filter(horario -> horario.getDiaSemana() == inicio.getDayOfWeek().getValue())
                .anyMatch(horario -> pertenceAoHorario(horario, duracaoMin, inicio, fim));

        if (!horarioValido) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este horário não está disponível");
        }
    }

    private boolean pertenceAoHorario(
            HorarioAtendimento horario,
            int duracaoMin,
            LocalDateTime inicio,
            LocalDateTime fim) {
        LocalDateTime inicioJanela = LocalDateTime.of(inicio.toLocalDate(), horario.getHoraInicio());
        LocalDateTime fimJanela = LocalDateTime.of(inicio.toLocalDate(), horario.getHoraFim());
        long deslocamentoSegundos = Duration.between(inicioJanela, inicio).toSeconds();
        long duracaoSegundos = Duration.ofMinutes(duracaoMin).toSeconds();

        return !inicio.isBefore(inicioJanela)
                && !fim.isAfter(fimJanela)
                && deslocamentoSegundos >= 0
                && deslocamentoSegundos % duracaoSegundos == 0;
    }

    private void validarDisponibilidade(Long prestadorId, LocalDateTime inicio, LocalDateTime fim) {
        boolean possuiConflito = agendamentoRepository
                .existsByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusIn(
                        prestadorId,
                        fim,
                        inicio,
                        STATUS_QUE_BLOQUEIAM_AGENDA
                );

        if (possuiConflito) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Horário indisponível. Este prestador já possui um agendamento neste horário"
            );
        }
    }
}
