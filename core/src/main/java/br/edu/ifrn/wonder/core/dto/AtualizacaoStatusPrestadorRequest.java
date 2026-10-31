package br.edu.ifrn.wonder.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizacaoStatusPrestadorRequest(
        @NotBlank String status,
        @JsonProperty("motivo_rejeicao") @Size(max = 1000) String motivoRejeicao
) {
}
