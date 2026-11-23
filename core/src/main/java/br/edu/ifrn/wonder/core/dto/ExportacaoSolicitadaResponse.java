package br.edu.ifrn.wonder.core.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExportacaoSolicitadaResponse(
        @JsonProperty("exportacao_id") UUID exportacaoId,
        String mensagem) {
}
