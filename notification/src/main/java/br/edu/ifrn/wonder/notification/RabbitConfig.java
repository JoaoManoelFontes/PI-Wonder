package br.edu.ifrn.wonder.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "wonder.events";
	public static final String PROVIDER_REQUEST_CREATED_QUEUE = "provider_request_created";
	public static final String PROVIDER_REQUEST_CREATED_ROUTING_KEY = "provider_request_created";
	public static final String PROVIDER_REQUEST_CREATED_DLQ = "provider_request_created.dlq";

	@Bean
	public JacksonJsonMessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}

	@Bean
	public DirectExchange wonderExchange() {
		return new DirectExchange(EXCHANGE);
	}

	@Bean
	public Queue providerRequestCreatedQueue() {
		return QueueBuilder.durable(PROVIDER_REQUEST_CREATED_QUEUE)
				.deadLetterExchange("").deadLetterRoutingKey(PROVIDER_REQUEST_CREATED_DLQ).build();
	}

	@Bean
	public Queue providerRequestCreatedDeadLetterQueue() {
		return QueueBuilder.durable(PROVIDER_REQUEST_CREATED_DLQ).build();
	}

	@Bean
	public Binding providerRequestCreatedBinding(@Qualifier("providerRequestCreatedQueue") Queue providerRequestCreatedQueue,
			DirectExchange wonderExchange) {
		return BindingBuilder.bind(providerRequestCreatedQueue).to(wonderExchange).with(PROVIDER_REQUEST_CREATED_ROUTING_KEY);
	}

}
