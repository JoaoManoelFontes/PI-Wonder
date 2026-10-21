package br.edu.ifrn.wonder.core.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CadastroServicoRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal preco,
        @NotNull @Min(1) @JsonProperty("duracao_min") Integer duracaoMin,
        @Positive @JsonProperty("categoria_id") Long categoriaId
) {
}
