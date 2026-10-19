package br.edu.ifrn.wonder.core.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.dto.DisponibilidadeResponse;
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

    private List<Agendamento> buscarAgendamentosDoDia(Long prestadorId, LocalDate data) {
        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = inicioDia.plusDays(1);

        return agendamentoRepository
                .findByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusNotOrderByInicioAsc(
                        prestadorId,
                        fimDia,
                        inicioDia,
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
