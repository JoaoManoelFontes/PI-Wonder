package br.edu.ifrn.wonder.worker.messaging;

import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.stereotype.Component;
import br.edu.ifrn.wonder.worker.dto.ExportacaoConcluida;

@Component
public class ExportacaoPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final JacksonJsonMessageConverter conversor = new JacksonJsonMessageConverter();

    public ExportacaoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(ExportacaoConcluida evento) throws Exception {
        var correlacao = new CorrelationData();
        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.COMPLETED_ROUTING_KEY,
                conversor.toMessage(evento, new MessageProperties()), correlacao);
        var confirmacao = correlacao.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirmacao.ack() || correlacao.getReturned() != null) {
            throw new IllegalStateException("Publicação de exportação sem confirmação ou sem rota");
        }
    }
}
