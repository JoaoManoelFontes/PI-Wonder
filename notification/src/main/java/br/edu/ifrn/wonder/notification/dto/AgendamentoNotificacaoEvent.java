package br.edu.ifrn.wonder.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgendamentoNotificacaoEvent(
        Long agendamentoId,
        UUID clienteKeycloakId,
        UUID prestadorKeycloakId,
        String nomeEstabelecimento,
        String nomeServico,
        LocalDateTime inicio,
        String statusNovo,
        String alteradoPorTipo,
        String motivo) {
}
