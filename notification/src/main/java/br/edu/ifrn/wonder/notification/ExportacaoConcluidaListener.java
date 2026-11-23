package br.edu.ifrn.wonder.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.edu.ifrn.wonder.notification.dto.ExportacaoConcluida;
import br.edu.ifrn.wonder.notification.service.EmailService;
import br.edu.ifrn.wonder.notification.service.ExportacaoConcluidaTemplate;

@Component
public class ExportacaoConcluidaListener {

    private final EmailService servicoEmail;
    private final ExportacaoConcluidaTemplate template;

    public ExportacaoConcluidaListener(EmailService servicoEmail, ExportacaoConcluidaTemplate template) {
        this.servicoEmail = servicoEmail;
        this.template = template;
    }

    @RabbitListener(queues = RabbitConfig.COMPLETED_QUEUE)
    public void handle(ExportacaoConcluida exportacao) {
        servicoEmail.enviar(template.renderizar(exportacao));
    }
}
