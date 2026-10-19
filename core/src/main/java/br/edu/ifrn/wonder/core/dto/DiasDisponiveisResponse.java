package br.edu.ifrn.wonder.core.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DiasDisponiveisResponse(
        @JsonProperty("prestador_id") Long prestadorId,
        @JsonProperty("servico_id") Long servicoId,
        String mes,
        List<DiaDisponivelResponse> dias
) {
}
