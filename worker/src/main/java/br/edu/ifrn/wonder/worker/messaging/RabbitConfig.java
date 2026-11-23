package br.edu.ifrn.wonder.worker.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "wonder.events";
    public static final String REPORT_GENERATION_QUEUE = "wonder.report.generation";
    public static final String REPORT_GENERATION_ROUTING_KEY = "report.generation.requested";
    public static final String REPORT_GENERATION_DLQ = "wonder.report.generation.dlq";
    public static final String COMPLETED_QUEUE = "wonder.notification.exportacao";
    public static final String COMPLETED_ROUTING_KEY = "report.generation.completed";
    public static final String COMPLETED_DLQ = "wonder.notification.exportacao.dlq";

    @Bean
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer configurador,
            org.springframework.amqp.rabbit.connection.ConnectionFactory conexao) {
        var fabrica = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        configurador.configure(fabrica, conexao);
        // O handler padrão pode registrar o corpo da mensagem e seus dados pessoais.
        fabrica.setErrorHandler(erro -> {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Exportação rejeitada; consultar DLQ");
        });
        return fabrica;
    }

    @Bean
    public Declarables topologiaExportacao() {
        var exchange = new DirectExchange(EXCHANGE);
        var entrada = QueueBuilder.durable(REPORT_GENERATION_QUEUE)
                .deadLetterExchange("").deadLetterRoutingKey(REPORT_GENERATION_DLQ).build();
        var saida = QueueBuilder.durable(COMPLETED_QUEUE)
                .deadLetterExchange("").deadLetterRoutingKey(COMPLETED_DLQ).build();
        return new Declarables(exchange, entrada, saida,
                QueueBuilder.durable(REPORT_GENERATION_DLQ).build(), QueueBuilder.durable(COMPLETED_DLQ).build(),
                BindingBuilder.bind(entrada).to(exchange).with(REPORT_GENERATION_ROUTING_KEY),
                BindingBuilder.bind(saida).to(exchange).with(COMPLETED_ROUTING_KEY));
    }
}
