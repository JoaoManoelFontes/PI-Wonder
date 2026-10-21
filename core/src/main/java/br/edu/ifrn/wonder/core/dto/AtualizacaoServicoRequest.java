package br.edu.ifrn.wonder.core.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AtualizacaoServicoRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "não deve estar em branco")
        @Size(max = 150) String nome,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal preco,
        @Min(1) @JsonProperty("duracao_min") Integer duracaoMin,
        @Positive @JsonProperty("categoria_id") Long categoriaId
) {
}
