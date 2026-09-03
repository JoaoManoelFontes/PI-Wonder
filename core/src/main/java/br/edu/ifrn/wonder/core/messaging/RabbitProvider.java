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
		rabbitTemplate.convertAndSend(RabbitConfig.NOTIFICATION_QUEUE, message);
	}

	public void sendReportGenerationJob(String message) {
		rabbitTemplate.convertAndSend(RabbitConfig.REPORT_GENERATION_QUEUE, message);
	}

}
