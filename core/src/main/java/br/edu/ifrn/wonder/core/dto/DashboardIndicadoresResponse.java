package br.edu.ifrn.wonder.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DashboardIndicadoresResponse(
        @JsonProperty("total_usuarios") long totalUsuarios,
        @JsonProperty("total_prestadores") long totalPrestadores,
        @JsonProperty("prestadores_pendentes") long prestadoresPendentes,
        @JsonProperty("atendimentos_concluidos_mes") long atendimentosConcluidosMes,
        @JsonProperty("mes_referencia") String mesReferencia
) {
}
