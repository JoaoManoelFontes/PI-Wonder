package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CadastroAgendamentoRequest(
        @NotNull @Positive @JsonProperty("prestador_id") Long prestadorId,
        @NotNull @Positive @JsonProperty("servico_id") Long servicoId,
        @NotNull @Future LocalDateTime inicio
) {
}
