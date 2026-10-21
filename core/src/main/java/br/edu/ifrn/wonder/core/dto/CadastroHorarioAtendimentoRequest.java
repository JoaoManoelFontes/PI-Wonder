package br.edu.ifrn.wonder.core.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CadastroHorarioAtendimentoRequest(
        @NotNull @Min(1) @Max(7) @JsonProperty("dia_semana") Integer diaSemana,
        @NotNull @JsonProperty("hora_inicio") LocalTime horaInicio,
        @NotNull @JsonProperty("hora_fim") LocalTime horaFim
) {
}
