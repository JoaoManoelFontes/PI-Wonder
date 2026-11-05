package br.edu.ifrn.wonder.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.edu.ifrn.wonder.notification.dto.ProviderRequestCreated;
import br.edu.ifrn.wonder.notification.service.EmailService;
import br.edu.ifrn.wonder.notification.service.ProviderRequestCreatedTemplate;

@Component
public class NotificationListener {

	private final EmailService emailService;
	private final ProviderRequestCreatedTemplate template;

	public NotificationListener(EmailService emailService, ProviderRequestCreatedTemplate template) {
		this.emailService = emailService;
		this.template = template;
	}

	@RabbitListener(queues = RabbitConfig.PROVIDER_REQUEST_CREATED_QUEUE)
	public void handle(ProviderRequestCreated solicitacao) {
		emailService.enviarEmLote(template.renderizar(solicitacao));
	}

}
