package br.edu.ifrn.wonder.core.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

	public static final String NOTIFICATION_QUEUE = "wonder.notification";
	public static final String REPORT_GENERATION_QUEUE = "wonder.report.generation";

	@Bean
	public Queue notificationQueue() {
		return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
	}

	@Bean
	public Queue exportQueue() {
		return QueueBuilder.durable(REPORT_GENERATION_QUEUE).build();
	}

}
