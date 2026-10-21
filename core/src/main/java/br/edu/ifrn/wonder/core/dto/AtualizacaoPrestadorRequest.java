package br.edu.ifrn.wonder.core.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AtualizacaoPrestadorRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "não deve estar em branco")
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
        String complemento
) {
}
