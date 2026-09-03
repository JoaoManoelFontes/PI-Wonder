package br.edu.ifrn.wonder.worker;

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
	public static final String REPORT_GENERATION_QUEUE = "wonder.report.generation";
	public static final String REPORT_GENERATION_ROUTING_KEY = "report.generation.requested";

	@Bean
	public DirectExchange wonderExchange() {
		return new DirectExchange(EXCHANGE);
	}

	@Bean
	public Queue reportGenerationQueue() {
		return QueueBuilder.durable(REPORT_GENERATION_QUEUE).build();
	}

	@Bean
	public Binding reportGenerationBinding(Queue reportGenerationQueue, DirectExchange wonderExchange) {
		return BindingBuilder.bind(reportGenerationQueue).to(wonderExchange).with(REPORT_GENERATION_ROUTING_KEY);
	}

}
