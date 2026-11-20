package br.edu.ifrn.wonder.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DashboardAgendamentosResponse(
        @JsonProperty("data_inicio") LocalDate dataInicio,
        @JsonProperty("data_fim") LocalDate dataFim,
        @JsonProperty("total_agendamentos") long totalAgendamentos,
        @JsonProperty("media_por_dia") BigDecimal mediaPorDia,
        @JsonProperty("totais_por_status") AgendamentosTotaisStatusResponse totaisPorStatus,
        List<AgendamentosDiaResponse> dias
) {
}
