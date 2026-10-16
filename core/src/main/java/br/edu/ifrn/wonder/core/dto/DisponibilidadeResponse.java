package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DisponibilidadeResponse(
        @JsonProperty("prestador_id") Long prestadorId,
        @JsonProperty("servico_id") Long servicoId,
        LocalDate data,
        @JsonProperty("duracao_min") Integer duracaoMin,
        List<HorarioDisponivelResponse> horarios
) {
}
