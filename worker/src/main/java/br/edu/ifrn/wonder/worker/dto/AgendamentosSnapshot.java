package br.edu.ifrn.wonder.worker.dto;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AgendamentosSnapshot(@JsonProperty("data_inicio") LocalDate dataInicio,
        @JsonProperty("data_fim") LocalDate dataFim,
        @JsonProperty("total_agendamentos") Long totalAgendamentos,
        @JsonProperty("media_por_dia") BigDecimal mediaPorDia,
        @JsonProperty("totais_por_status") Totais totaisPorStatus, List<Dia> dias) {
    public record Totais(Long pendente, Long confirmado, Long cancelado, Long concluido) {}
    public record Dia(LocalDate data, @JsonProperty("total_agendamentos") Long totalAgendamentos,
            @JsonProperty("totais_por_status") Totais totaisPorStatus) {}
}
