package br.edu.ifrn.wonder.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroCategoriaRequest(
        @NotBlank @Size(max = 100) String nome,
        String descricao
) {
}
