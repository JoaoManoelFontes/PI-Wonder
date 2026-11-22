package br.edu.ifrn.wonder.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.edu.ifrn.wonder.notification.dto.AgendamentoNotificacaoEvent;
import br.edu.ifrn.wonder.notification.service.AgendamentoNotificacaoService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AgendamentoNotificacaoListener {

    private final AgendamentoNotificacaoService service;

    @RabbitListener(queues = RabbitConfig.APPOINTMENT_NOTIFICATION_QUEUE)
    public void handle(AgendamentoNotificacaoEvent evento) {
        service.processar(evento);
    }
}
