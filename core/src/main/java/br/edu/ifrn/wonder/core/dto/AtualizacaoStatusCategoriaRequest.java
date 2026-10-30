package br.edu.ifrn.wonder.core.dto;

import jakarta.validation.constraints.NotBlank;

public record AtualizacaoStatusCategoriaRequest(
        @NotBlank String status
) {
}
