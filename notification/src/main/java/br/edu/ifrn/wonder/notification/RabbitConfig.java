package br.edu.ifrn.wonder.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "wonder.events";
	public static final String NOTIFICATION_QUEUE = "wonder.notification";
	public static final String NOTIFICATION_ROUTING_KEY = "notification.requested";

	@Bean
	public DirectExchange wonderExchange() {
		return new DirectExchange(EXCHANGE);
	}

	@Bean
	public Queue notificationQueue() {
		return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
	}

	@Bean
	public Binding notificationBinding(Queue notificationQueue, DirectExchange wonderExchange) {
		return BindingBuilder.bind(notificationQueue).to(wonderExchange).with(NOTIFICATION_ROUTING_KEY);
	}

}
