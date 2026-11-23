package br.edu.ifrn.wonder.worker.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import br.edu.ifrn.wonder.worker.dto.*;

class ExportacaoPublisherTest {
    @ParameterizedTest
    @ValueSource(strings = {"ack", "nack", "retorno", "timeout"})
    void exigeAckSemRetornoETimeoutLimitado(String resultado) throws Exception {
        var rabbit = mock(RabbitTemplate.class);
        doAnswer(chamada -> {
            Message mensagem = chamada.getArgument(2);
            assertThat(mensagem.getMessageProperties().getContentType()).isEqualTo("application/json");
            CorrelationData correlacao = chamada.getArgument(3);
            if (resultado.equals("retorno")) correlacao.setReturned(new ReturnedMessage(mensagem, 312, "NO_ROUTE", "wonder.events", "report.generation.completed"));
            if (!resultado.equals("timeout")) correlacao.getFuture().complete(new CorrelationData.Confirm(!resultado.equals("nack"), null));
            return null;
        }).when(rabbit).send(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.COMPLETED_ROUTING_KEY), any(Message.class), any(CorrelationData.class));
        var evento = new ExportacaoConcluida(UUID.randomUUID(), ExportacaoSolicitada.Tipo.USUARIOS,
                "ana@example.com", "Ana", "usuarios.csv", "https://example.com/arquivo", Instant.now());
        var publicador = new ExportacaoPublisher(rabbit);
        if (resultado.equals("ack")) {
            publicador.publicar(evento);
        } else if (resultado.equals("timeout")) {
            assertThatThrownBy(() -> publicador.publicar(evento)).isInstanceOf(java.util.concurrent.TimeoutException.class);
        } else {
            assertThatThrownBy(() -> publicador.publicar(evento)).isInstanceOf(IllegalStateException.class);
        }
    }
}
