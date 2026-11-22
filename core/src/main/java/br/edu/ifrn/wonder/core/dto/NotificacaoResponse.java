package br.edu.ifrn.wonder.core.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.Notificacao;

public record NotificacaoResponse(
        Long id,
        @JsonProperty("usuario_id") UUID usuarioId,
        String tipo,
        String mensagem,
        String status,
        @JsonProperty("criado_em") Instant criadoEm) {

    public static NotificacaoResponse from(Notificacao notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getUsuarioKeycloakId(),
                notificacao.getTipo(),
                notificacao.getMensagem(),
                notificacao.getStatus().name().toLowerCase(),
                notificacao.getCriadoEm());
    }
}
