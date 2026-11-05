package br.edu.ifrn.wonder.core.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.stereotype.Component;

import br.edu.ifrn.wonder.core.dto.ProviderRequestCreated;

@Component
public class RabbitProvider {

	private final RabbitTemplate rabbitTemplate;
	private final JacksonJsonMessageConverter conversorJson = new JacksonJsonMessageConverter();

	public RabbitProvider(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void sendProviderRequestCreated(ProviderRequestCreated solicitacao) {
		rabbitTemplate.send(
				RabbitConfig.EXCHANGE,
				RabbitConfig.PROVIDER_REQUEST_CREATED_ROUTING_KEY,
				conversorJson.toMessage(solicitacao, new MessageProperties())
		);
	}

	public void sendReportGenerationJob(String message) {
		rabbitTemplate.convertAndSend(
				RabbitConfig.EXCHANGE,
				RabbitConfig.REPORT_GENERATION_ROUTING_KEY,
				message
		);
	}

}
