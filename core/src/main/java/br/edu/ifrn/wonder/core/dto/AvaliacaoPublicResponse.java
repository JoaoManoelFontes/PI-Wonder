package br.edu.ifrn.wonder.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.Avaliacao;

public record AvaliacaoPublicResponse(
        Long id,
        @JsonProperty("agendamento_id") Long agendamentoId,
        @JsonProperty("prestador_id") Long prestadorId,
        Integer nota
) {

    public static AvaliacaoPublicResponse from(Avaliacao avaliacao) {
        return new AvaliacaoPublicResponse(
                avaliacao.getId(),
                avaliacao.getAgendamento().getId(),
                avaliacao.getAgendamento().getPrestador().getId(),
                avaliacao.getNota());
    }
}
