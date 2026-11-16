package br.edu.ifrn.wonder.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.dto.DashboardIndicadoresResponse;
import br.edu.ifrn.wonder.core.dto.AgendamentosDiaResponse;
import br.edu.ifrn.wonder.core.dto.AgendamentosTotaisStatusResponse;
import br.edu.ifrn.wonder.core.dto.DashboardAgendamentosResponse;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final ZoneId FUSO_SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final KeycloakAdminService keycloakAdminService;
    private final PrestadorRepository prestadorRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final Clock relogio;

    public DashboardAgendamentosResponse consultarAgendamentos(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null && dataFim == null) {
            YearMonth mesReferencia = YearMonth.now(relogio.withZone(FUSO_SAO_PAULO));
            dataInicio = mesReferencia.atDay(1);
            dataFim = mesReferencia.atEndOfMonth();
        }
        if (dataInicio == null || dataFim == null || dataInicio.isAfter(dataFim)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe data_inicio e data_fim, com início anterior ou igual ao fim");
        }

        LocalDate fimExclusivo;
        try {
            fimExclusivo = dataFim.plusDays(1);
        } catch (DateTimeException excecao) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intervalo de datas inválido", excecao);
        }

        Map<LocalDate, EnumMap<StatusAgendamento, Long>> contagensPorDia = new HashMap<>();
        var totaisPeriodo = new EnumMap<StatusAgendamento, Long>(StatusAgendamento.class);
        for (var contagem : agendamentoRepository.contarPorDiaEStatus(
                dataInicio.atStartOfDay(), fimExclusivo.atStartOfDay())) {
            StatusAgendamento status = StatusAgendamento.valueOf(contagem.getStatus());
            contagensPorDia.computeIfAbsent(contagem.getData(),
                    data -> new EnumMap<>(StatusAgendamento.class))
                    .merge(status, contagem.getQuantidade(), Long::sum);
            totaisPeriodo.merge(status, contagem.getQuantidade(), Long::sum);
        }

        var dias = new ArrayList<AgendamentosDiaResponse>();
        for (LocalDate data = dataInicio; data.isBefore(fimExclusivo); data = data.plusDays(1)) {
            var totaisDia = totaisPorStatus(contagensPorDia.getOrDefault(data,
                    new EnumMap<>(StatusAgendamento.class)));
            dias.add(new AgendamentosDiaResponse(data, totaisDia.total(), totaisDia));
        }

        var totais = totaisPorStatus(totaisPeriodo);
        long quantidadeDias = ChronoUnit.DAYS.between(dataInicio, fimExclusivo);
        BigDecimal media = BigDecimal.valueOf(totais.total())
                .divide(BigDecimal.valueOf(quantidadeDias), 2, RoundingMode.HALF_UP);
        return new DashboardAgendamentosResponse(dataInicio, dataFim, totais.total(), media, totais, dias);
    }

    private AgendamentosTotaisStatusResponse totaisPorStatus(Map<StatusAgendamento, Long> contagens) {
        return new AgendamentosTotaisStatusResponse(
                contagens.getOrDefault(StatusAgendamento.PENDENTE, 0L),
                contagens.getOrDefault(StatusAgendamento.CONFIRMADO, 0L),
                contagens.getOrDefault(StatusAgendamento.CANCELADO, 0L),
                contagens.getOrDefault(StatusAgendamento.CONCLUIDO, 0L));
    }

    public DashboardIndicadoresResponse consultarIndicadores() {
        YearMonth mesReferencia = YearMonth.now(relogio.withZone(FUSO_SAO_PAULO));
        LocalDateTime inicioMes = mesReferencia.atDay(1).atStartOfDay();
        LocalDateTime inicioProximoMes = mesReferencia.plusMonths(1).atDay(1).atStartOfDay();

        long totalUsuarios = keycloakAdminService.contarUsuarios();
        long totalPrestadores = prestadorRepository.count();
        long prestadoresPendentes = prestadorRepository.countByStatus(StatusPrestador.PENDENTE);
        long atendimentosConcluidosMes = agendamentoRepository
                .countByStatusAndInicioGreaterThanEqualAndInicioLessThan(
                        StatusAgendamento.CONCLUIDO, inicioMes, inicioProximoMes);

        return new DashboardIndicadoresResponse(totalUsuarios, totalPrestadores,
                prestadoresPendentes, atendimentosConcluidosMes, mesReferencia.toString());
    }
}
