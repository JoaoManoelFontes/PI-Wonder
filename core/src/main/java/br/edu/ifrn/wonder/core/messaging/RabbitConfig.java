package br.edu.ifrn.wonder.core.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "wonder.events";
	public static final String NOTIFICATION_QUEUE = "wonder.notification";
	public static final String REPORT_GENERATION_QUEUE = "wonder.report.generation";
	public static final String NOTIFICATION_ROUTING_KEY = "notification.requested";
	public static final String REPORT_GENERATION_ROUTING_KEY = "report.generation.requested";

	@Bean
	public DirectExchange wonderExchange() {
		return new DirectExchange(EXCHANGE);
	}

	@Bean
	public Queue notificationQueue() {
		return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
	}

	@Bean
	public Queue reportGenerationQueue() {
		return QueueBuilder.durable(REPORT_GENERATION_QUEUE).build();
	}

	@Bean
	public Binding notificationBinding(
			@Qualifier("notificationQueue") Queue notificationQueue,
			DirectExchange wonderExchange
	) {
		return BindingBuilder.bind(notificationQueue).to(wonderExchange).with(NOTIFICATION_ROUTING_KEY);
	}

	@Bean
	public Binding reportGenerationBinding(
			@Qualifier("reportGenerationQueue") Queue reportGenerationQueue,
			DirectExchange wonderExchange
	) {
		return BindingBuilder.bind(reportGenerationQueue).to(wonderExchange).with(REPORT_GENERATION_ROUTING_KEY);
	}

}
