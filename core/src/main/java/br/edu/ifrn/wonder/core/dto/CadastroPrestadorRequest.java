package br.edu.ifrn.wonder.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroPrestadorRequest(
        @NotBlank
        @Size(max = 255)
        String nomeEstab,

        @Size(max = 20)
        String documento,

        @NotBlank
        @Size(max = 255)
        String endereco,

        @NotBlank
        @Size(max = 30)
        String numero,

        @NotBlank
        @Size(max = 120)
        String bairro,

        @NotBlank
        @Size(max = 120)
        String cidade,

        @NotBlank
        @Size(max = 2)
        String estado,

        @Size(max = 255)
        String complemento
) {
}
