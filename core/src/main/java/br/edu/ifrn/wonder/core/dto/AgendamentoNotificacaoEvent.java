package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Contrato do evento de agendamento consumido pelo módulo notification. */
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
