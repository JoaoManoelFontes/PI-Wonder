package br.edu.ifrn.wonder.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import br.edu.ifrn.wonder.core.dto.ProviderRequestCreated;
import br.edu.ifrn.wonder.core.dto.ProviderRequestReviewed;
import br.edu.ifrn.wonder.core.dto.ProviderRequestReviewed.Resultado;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import br.edu.ifrn.wonder.core.dto.ProviderRequestCreated.DadosPrestador;
import tools.jackson.databind.json.JsonMapper;

class RabbitProviderTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitProvider publicador = new RabbitProvider(rabbitTemplate);

    @Test
    void devePublicarSolicitacaoComoJsonNaRotaCorreta() {
        var solicitacao = new ProviderRequestCreated(List.of("admin@example.com"),
                new DadosPrestador(42L, "Salão São José", "12345678900", "Natal", "RN"));
        publicador.sendProviderRequestCreated(solicitacao);

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("wonder.events"), eq("provider_request_created"), captor.capture());
        var mensagem = captor.getValue();
        assertThat(mensagem.getMessageProperties().getContentType()).isEqualTo("application/json");
        var json = JsonMapper.builder().build().readTree(new String(mensagem.getBody(), StandardCharsets.UTF_8));
        assertThat(json.path("destinatarios").get(0).asString()).isEqualTo("admin@example.com");
        assertThat(json.path("prestador").path("id").asLong()).isEqualTo(42L);
        assertThat(json.path("prestador").path("nomeEstab").asString()).isEqualTo("Salão São José");
    }

    @Test
    void devePreservarPublicacaoDeRelatoriosComoTexto() {
        publicador.sendReportGenerationJob("relatorio");
        verify(rabbitTemplate).convertAndSend("wonder.events", "report.generation.requested", "relatorio");
    }

    @ParameterizedTest
    @EnumSource(Resultado.class)
    void devePublicarResultadoComoJsonNaRotaCorreta(Resultado resultado) {
        var solicitacao = new ProviderRequestReviewed(42L, "ana@example.com", "Salão São José",
                resultado, resultado == Resultado.REJEITADO ? "Endereço incompleto" : null);
        publicador.sendProviderRequestReviewed(solicitacao);

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("wonder.events"), eq("provider_request_reviewed"), captor.capture());
        var mensagem = captor.getValue();
        assertThat(mensagem.getMessageProperties().getContentType()).isEqualTo("application/json");
        var json = JsonMapper.builder().build().readTree(new String(mensagem.getBody(), StandardCharsets.UTF_8));
        assertThat(json.path("prestadorId").asLong()).isEqualTo(42L);
        assertThat(json.path("destinatario").asString()).isEqualTo("ana@example.com");
        assertThat(json.path("nomeEstabelecimento").asString()).isEqualTo("Salão São José");
        assertThat(json.path("resultado").asString()).isEqualTo(resultado.name());
        if (resultado == Resultado.REJEITADO) {
            assertThat(json.path("motivoRejeicao").asString()).isEqualTo("Endereço incompleto");
        } else {
            assertThat(json.path("motivoRejeicao").isNull()).isTrue();
        }
    }
}
