package br.edu.ifrn.wonder.core.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitProvider {

	private final RabbitTemplate rabbitTemplate;

	public RabbitProvider(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void sendNotification(String message) {
		rabbitTemplate.convertAndSend(
				RabbitConfig.EXCHANGE,
				RabbitConfig.NOTIFICATION_ROUTING_KEY,
				message
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
