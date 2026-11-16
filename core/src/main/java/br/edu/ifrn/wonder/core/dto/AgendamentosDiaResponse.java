package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgendamentosDiaResponse(
        LocalDate data,
        @JsonProperty("total_agendamentos") long totalAgendamentos,
        @JsonProperty("totais_por_status") AgendamentosTotaisStatusResponse totaisPorStatus
) {
}
