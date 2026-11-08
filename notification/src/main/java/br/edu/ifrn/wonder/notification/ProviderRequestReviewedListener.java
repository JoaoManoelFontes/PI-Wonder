package br.edu.ifrn.wonder.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.edu.ifrn.wonder.notification.dto.ProviderRequestReviewed;
import br.edu.ifrn.wonder.notification.service.EmailService;
import br.edu.ifrn.wonder.notification.service.ProviderRequestReviewedTemplate;

@Component
public class ProviderRequestReviewedListener {

    private final EmailService servicoEmail;
    private final ProviderRequestReviewedTemplate template;

    public ProviderRequestReviewedListener(EmailService servicoEmail, ProviderRequestReviewedTemplate template) {
        this.servicoEmail = servicoEmail;
        this.template = template;
    }

    @RabbitListener(queues = RabbitConfig.PROVIDER_REQUEST_REVIEWED_QUEUE)
    public void handle(ProviderRequestReviewed solicitacao) {
        servicoEmail.enviar(template.renderizar(solicitacao));
    }
}
