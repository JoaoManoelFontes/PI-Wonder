package br.edu.ifrn.wonder.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizacaoStatusAgendamentoRequest(
        @NotBlank String status,
        @Size(max = 1000) String motivo
) {
}
