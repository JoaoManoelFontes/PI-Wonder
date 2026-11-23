package br.edu.ifrn.wonder.core.service;

import java.time.LocalDateTime;

import org.springframework.amqp.AmqpException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.dto.AgendamentoNotificacaoEvent;
import br.edu.ifrn.wonder.core.messaging.RabbitProvider;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LembreteAgendamentoService {

    private static final String TIPO = "lembrete_agendamento";

    private final AgendamentoRepository agendamentoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final RabbitProvider rabbitProvider;

    @Scheduled(cron = "${wonder.notifications.reminder-cron:0 0 * * * *}")
    @Transactional(readOnly = true)
    public void publicarLembretes() {
        LocalDateTime agora = LocalDateTime.now();
        agendamentoRepository.findByStatusAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                        StatusAgendamento.CONFIRMADO, agora, agora.plusHours(24))
                .stream()
                .filter(this::aindaNaoNotificado)
                .forEach(this::publicar);
    }

    private boolean aindaNaoNotificado(Agendamento agendamento) {
        String chave = chave(agendamento);
        return !notificacaoRepository.existsByChaveEventoAndUsuarioKeycloakIdAndTipo(
                        chave, agendamento.getCliente().getKeycloakId(), TIPO)
                || !notificacaoRepository.existsByChaveEventoAndUsuarioKeycloakIdAndTipo(
                        chave, agendamento.getPrestador().getPerfilUsuario().getKeycloakId(), TIPO);
    }

    private void publicar(Agendamento agendamento) {
        try {
            rabbitProvider.sendAppointmentNotification(new AgendamentoNotificacaoEvent(
                    agendamento.getId(),
                    agendamento.getCliente().getKeycloakId(),
                    agendamento.getPrestador().getPerfilUsuario().getKeycloakId(),
                    agendamento.getPrestador().getNomeEstab(),
                    agendamento.getServico().getNome(),
                    agendamento.getInicio(),
                    "lembrete",
                    "sistema",
                    null));
        } catch (AmqpException exception) {
            log.error("Falha ao publicar lembrete do agendamento {}", agendamento.getId(), exception);
        }
    }

    private String chave(Agendamento agendamento) {
        return "agendamento:" + agendamento.getId() + ":lembrete";
    }
}
