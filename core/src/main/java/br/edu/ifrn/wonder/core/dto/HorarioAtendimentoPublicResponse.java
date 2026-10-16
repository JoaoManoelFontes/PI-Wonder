package br.edu.ifrn.wonder.core.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;

public record HorarioAtendimentoPublicResponse(
        Long id,
        @JsonProperty("prestador_id") Long prestadorId,
        @JsonProperty("dia_semana") Integer diaSemana,
        @JsonProperty("hora_inicio") LocalTime horaInicio,
        @JsonProperty("hora_fim") LocalTime horaFim
) {

    public static HorarioAtendimentoPublicResponse from(HorarioAtendimento horario) {
        return new HorarioAtendimentoPublicResponse(
                horario.getId(),
                horario.getPrestador().getId(),
                horario.getDiaSemana(),
                horario.getHoraInicio(),
                horario.getHoraFim()
        );
    }
}
