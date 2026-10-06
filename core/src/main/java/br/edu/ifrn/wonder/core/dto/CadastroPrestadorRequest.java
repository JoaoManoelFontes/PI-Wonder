package br.edu.ifrn.wonder.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroPrestadorRequest(
        @NotBlank
        @Size(max = 255)
        String nomeEstab,

        @Size(max = 20)
        String documento,

        @Size(max = 255)
        String endereco,

        @Size(max = 30)
        String numero,

        @Size(max = 120)
        String bairro,

        @Size(max = 120)
        String cidade,

        @Size(max = 2)
        String estado,

        @Size(max = 255)
        String complemento,

        @Size(max = 500)
        String fotoUrl
) {
}
