package br.edu.ifrn.wonder.core.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.dto.DisponibilidadeResponse;
import br.edu.ifrn.wonder.core.dto.DiaDisponivelResponse;
import br.edu.ifrn.wonder.core.dto.DiasDisponiveisResponse;
import br.edu.ifrn.wonder.core.dto.HorarioDisponivelResponse;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisponibilidadeService {

    private final CatalogoPublicoService catalogoPublicoService;
    private final HorarioAtendimentoRepository horarioAtendimentoRepository;
    private final AgendamentoRepository agendamentoRepository;

    public DisponibilidadeResponse calcular(Long prestadorId, Long servicoId, LocalDate data) {
        Servico servico = catalogoPublicoService.buscarServicoAtivo(prestadorId, servicoId);
        List<HorarioAtendimento> horarios = horarioAtendimentoRepository
                .findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestadorId);
        List<Agendamento> agendamentos = buscarAgendamentosDoDia(prestadorId, data);

        List<HorarioDisponivelResponse> horariosLivres = gerarHorariosLivres(
                data,
                servico.getDuracaoMin(),
                horarios,
                agendamentos);

        return new DisponibilidadeResponse(
                prestadorId,
                servicoId,
                data,
                servico.getDuracaoMin(),
                horariosLivres);
    }

    public DiasDisponiveisResponse calcularDiasDisponiveis(
            Long prestadorId,
            Long servicoId,
            YearMonth mes) {
        Servico servico = catalogoPublicoService.buscarServicoAtivo(prestadorId, servicoId);
        List<HorarioAtendimento> horarios = horarioAtendimentoRepository
                .findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestadorId);
        List<Agendamento> agendamentos = buscarAgendamentosDoPeriodo(
                prestadorId,
                mes.atDay(1).atStartOfDay(),
                mes.plusMonths(1).atDay(1).atStartOfDay());
        List<DiaDisponivelResponse> dias = new ArrayList<>();

        for (LocalDate data = mes.atDay(1); !data.isAfter(mes.atEndOfMonth()); data = data.plusDays(1)) {
            List<HorarioDisponivelResponse> horariosLivres = gerarHorariosLivres(
                    data,
                    servico.getDuracaoMin(),
                    horarios,
                    agendamentos);

            if (!horariosLivres.isEmpty()) {
                dias.add(new DiaDisponivelResponse(data, true));
            }
        }

        return new DiasDisponiveisResponse(
                prestadorId,
                servicoId,
                mes.toString(),
                dias);
    }

    private List<Agendamento> buscarAgendamentosDoDia(Long prestadorId, LocalDate data) {
        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = inicioDia.plusDays(1);

        return buscarAgendamentosDoPeriodo(prestadorId, inicioDia, fimDia);
    }

    private List<Agendamento> buscarAgendamentosDoPeriodo(
            Long prestadorId,
            LocalDateTime inicio,
            LocalDateTime fim) {

        return agendamentoRepository
                .findByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusNotOrderByInicioAsc(
                        prestadorId,
                        fim,
                        inicio,
                        StatusAgendamento.CANCELADO);
    }

    private List<HorarioDisponivelResponse> gerarHorariosLivres(
            LocalDate data,
            int duracaoMin,
            List<HorarioAtendimento> horarios,
            List<Agendamento> agendamentos) {
        int diaSemana = data.getDayOfWeek().getValue();
        List<HorarioDisponivelResponse> horariosLivres = new ArrayList<>();

        for (HorarioAtendimento horario : horarios) {
            if (horario.getDiaSemana() != diaSemana) {
                continue;
            }

            LocalDateTime inicioJanela = LocalDateTime.of(data, horario.getHoraInicio());
            LocalDateTime fimJanela = LocalDateTime.of(data, horario.getHoraFim());

            for (LocalDateTime inicio = inicioJanela;
                    !inicio.plusMinutes(duracaoMin).isAfter(fimJanela);
                    inicio = inicio.plusMinutes(duracaoMin)) {
                LocalDateTime fim = inicio.plusMinutes(duracaoMin);

                if (!possuiConflito(inicio, fim, agendamentos)) {
                    horariosLivres.add(new HorarioDisponivelResponse(inicio, fim));
                }
            }
        }

        return horariosLivres;
    }

    private boolean possuiConflito(
            LocalDateTime inicio,
            LocalDateTime fim,
            List<Agendamento> agendamentos) {
        return agendamentos.stream().anyMatch(agendamento ->
                agendamento.getStatus() != StatusAgendamento.CANCELADO
                        && agendamento.getInicio().isBefore(fim)
                        && agendamento.getFim().isAfter(inicio));
    }
}
