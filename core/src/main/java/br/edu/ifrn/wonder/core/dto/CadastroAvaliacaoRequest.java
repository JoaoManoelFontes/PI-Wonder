package br.edu.ifrn.wonder.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CadastroAvaliacaoRequest(
        @NotNull @Positive @JsonProperty("agendamento_id") Long agendamentoId,
        @NotNull @Min(1) @Max(5) Integer nota
) {
}
