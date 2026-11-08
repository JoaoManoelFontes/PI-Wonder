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
	public static final String PROVIDER_REQUEST_REVIEWED_QUEUE = "provider_request_reviewed";
	public static final String PROVIDER_REQUEST_REVIEWED_ROUTING_KEY = "provider_request_reviewed";
	public static final String PROVIDER_REQUEST_REVIEWED_DLQ = "provider_request_reviewed.dlq";

	@Bean
	public Queue providerRequestReviewedQueue() {
		return QueueBuilder.durable(PROVIDER_REQUEST_REVIEWED_QUEUE)
				.deadLetterExchange("").deadLetterRoutingKey(PROVIDER_REQUEST_REVIEWED_DLQ).build();
	}

	@Bean
	public Queue providerRequestReviewedDeadLetterQueue() {
		return QueueBuilder.durable(PROVIDER_REQUEST_REVIEWED_DLQ).build();
	}

	@Bean
	public Binding providerRequestReviewedBinding(
			@Qualifier("providerRequestReviewedQueue") Queue fila,
			DirectExchange wonderExchange) {
		return BindingBuilder.bind(fila).to(wonderExchange).with(PROVIDER_REQUEST_REVIEWED_ROUTING_KEY);
	}
	public static final String PROVIDER_REQUEST_CREATED_QUEUE = "provider_request_created";
	public static final String REPORT_GENERATION_QUEUE = "wonder.report.generation";
	public static final String PROVIDER_REQUEST_CREATED_ROUTING_KEY = "provider_request_created";
	public static final String PROVIDER_REQUEST_CREATED_DLQ = "provider_request_created.dlq";
	public static final String REPORT_GENERATION_ROUTING_KEY = "report.generation.requested";

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
	public Queue reportGenerationQueue() {
		return QueueBuilder.durable(REPORT_GENERATION_QUEUE).build();
	}

	@Bean
	public Binding providerRequestCreatedBinding(
			@Qualifier("providerRequestCreatedQueue") Queue providerRequestCreatedQueue,
			DirectExchange wonderExchange
	) {
		return BindingBuilder.bind(providerRequestCreatedQueue).to(wonderExchange).with(PROVIDER_REQUEST_CREATED_ROUTING_KEY);
	}

	@Bean
	public Binding reportGenerationBinding(
			@Qualifier("reportGenerationQueue") Queue reportGenerationQueue,
			DirectExchange wonderExchange
	) {
		return BindingBuilder.bind(reportGenerationQueue).to(wonderExchange).with(REPORT_GENERATION_ROUTING_KEY);
	}

}
