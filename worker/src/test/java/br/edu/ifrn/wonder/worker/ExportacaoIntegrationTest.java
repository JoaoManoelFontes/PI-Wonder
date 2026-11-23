package br.edu.ifrn.wonder.worker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import br.edu.ifrn.wonder.worker.config.MinioProperties;
import br.edu.ifrn.wonder.worker.dto.ExportacaoConcluida;
import br.edu.ifrn.wonder.worker.messaging.*;
import br.edu.ifrn.wonder.worker.service.*;
import io.minio.*;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExportacaoIntegrationTest {
    @Autowired RabbitTemplate rabbit;
    @Autowired @Qualifier("minioInterno") MinioClient minio;
    @Autowired MinioProperties configuracao;
    @Autowired ContratoExportacaoService contrato;
    @Autowired GeradorCsvService gerador;
    @MockitoSpyBean ArmazenamentoExportacaoService armazenamento;
    @MockitoSpyBean ExportacaoPublisher publicador;
    private final JsonMapper conversor = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void limparFilas() {
        reset(armazenamento, publicador);
        var administrador = new RabbitAdmin(rabbit.getConnectionFactory());
        administrador.purgeQueue(RabbitConfig.COMPLETED_QUEUE);
        administrador.purgeQueue(RabbitConfig.REPORT_GENERATION_DLQ);
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuarios", "prestadores", "agendamentos"})
    void geraArmazenaBaixaEDeixaEventoNaFilaSemNotification(String tipo) throws Exception {
        UUID id = UUID.randomUUID();
        var mensagem = Contratos.mensagem(tipo, id);
        enviar(mensagem);
        // Sem consumidor: a conclusão permanece na fila até este receive explícito.
        var saida = rabbit.receive(RabbitConfig.COMPLETED_QUEUE, 15000);
        assertThat(saida).isNotNull();
        assertThat(saida.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(saida.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        var json = conversor.readTree(saida.getBody());
        assertThat(json.size()).isEqualTo(7);
        var evento = conversor.readValue(saida.getBody(), ExportacaoConcluida.class);
        assertThat(evento.exportacaoId()).isEqualTo(id);
        assertThat(evento.tipo().name()).isEqualTo(tipo.toUpperCase(java.util.Locale.ROOT));
        assertThat(evento.email()).isEqualTo("ana@example.com");
        assertThat(evento.nome()).isEqualTo("Ana Silva");
        assertThat(evento.arquivoUrl()).startsWith(configuracao.publicEndpoint() + "/");
        assertThat(configuracao.endpoint()).isNotEqualTo(configuracao.publicEndpoint());
        assertThat(evento.expiraEm()).isBetween(Instant.now().plusSeconds(86380), Instant.now().plusSeconds(86401));
        assertThat(evento.arquivoUrl()).contains("X-Amz-Expires=86400");

        var pedido = contrato.ler(mensagem.getBody());
        StringWriter esperado = new StringWriter();
        gerador.escrever(esperado, pedido.tipo(), contrato.snapshot(pedido));
        byte[] bytes = esperado.toString().getBytes(StandardCharsets.UTF_8);
        var download = baixar(evento.arquivoUrl());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(bytes);
        var demonstracao = java.nio.file.Path.of("target", "demonstracao");
        java.nio.file.Files.createDirectories(demonstracao);
        java.nio.file.Files.write(demonstracao.resolve(tipo + ".csv"), download.body());
        assertThat(download.headers().firstValue("Content-Type")).contains(ArmazenamentoExportacaoService.CONTENT_TYPE);
        assertThat(download.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"" + evento.nomeArquivo() + "\"");
        String chave = "exportacoes/" + tipo + "/" + id + ".csv";
        var metadados = minio.statObject(StatObjectArgs.builder().bucket(configuracao.exportBucket()).object(chave).build());
        assertThat(metadados.contentType()).isEqualTo(ArmazenamentoExportacaoService.CONTENT_TYPE);
        assertThat(metadados.headers().get("Content-Disposition")).isEqualTo("attachment; filename=\"" + evento.nomeArquivo() + "\"");
        assertThat(baixar(configuracao.publicEndpoint() + "/" + configuracao.exportBucket() + "/" + chave).statusCode()).isEqualTo(403);
        assertThat(rabbit.receive(RabbitConfig.REPORT_GENERATION_DLQ)).isNull();

        // Reentrega: mesmo objeto, mesmo ID e nova conclusão (sem promessa de exactly-once).
        enviar(mensagem);
        var repetido = rabbit.receive(RabbitConfig.COMPLETED_QUEUE, 15000);
        assertThat(repetido).isNotNull();
        assertThat(conversor.readValue(repetido.getBody(), ExportacaoConcluida.class).exportacaoId()).isEqualTo(id);
        int quantidade = 0;
        for (var objeto : minio.listObjects(ListObjectsArgs.builder().bucket(configuracao.exportBucket()).prefix(chave).build())) {
            assertThat(objeto.get().objectName()).isEqualTo(chave);
            quantidade++;
        }
        assertThat(quantidade).isEqualTo(1);
    }

    @Test
    void falhaTransitoriaTentaNovamenteEConclui() throws Exception {
        doThrow(new java.io.IOException("Falha transitória simulada")).doCallRealMethod()
                .when(armazenamento).armazenar(any(), anyString(), any());
        enviar(Contratos.mensagem("usuarios", UUID.randomUUID()));
        assertThat(rabbit.receive(RabbitConfig.COMPLETED_QUEUE, 15000)).isNotNull();
        verify(armazenamento, times(2)).armazenar(any(), anyString(), any());
        assertThat(rabbit.receive(RabbitConfig.REPORT_GENERATION_DLQ)).isNull();
    }

    @Test
    void tresFalhasLevamOriginalADlqSemNotificacao() throws Exception {
        doThrow(new java.io.IOException("Falha persistente simulada"))
                .when(armazenamento).armazenar(any(), anyString(), any());
        var mensagem = Contratos.mensagem("usuarios", UUID.randomUUID());
        enviar(mensagem);
        var rejeitada = rabbit.receive(RabbitConfig.REPORT_GENERATION_DLQ, 15000);
        assertThat(rejeitada).isNotNull();
        assertThat(rejeitada.getBody()).isEqualTo(mensagem.getBody());
        assertThat(rejeitada.getMessageProperties().getHeaders()).containsKey("x-death");
        verify(armazenamento, times(3)).armazenar(any(), anyString(), any());
        verifyNoInteractions(publicador);
        assertThat(rabbit.receive(RabbitConfig.COMPLETED_QUEUE)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"tipo\":\"DESCONHECIDO\"}",
            "{\"exportacaoId\":\"14128674-d423-4b67-91f5-dca790dc3940\",\"tipo\":\"USUARIOS\",\"email\":\"ana@example.com\",\"nome\":\"Ana\",\"dados\":{}}"})
    void invalidoVaiDiretoParaDlqSemUploadOuPublicacao(String json) {
        enviar(new Message(json.getBytes(StandardCharsets.UTF_8)));
        assertThat(rabbit.receive(RabbitConfig.REPORT_GENERATION_DLQ, 10000)).isNotNull();
        verifyNoInteractions(armazenamento, publicador);
        assertThat(rabbit.receive(RabbitConfig.COMPLETED_QUEUE)).isNull();
    }

    @Test
    void ausenciaDeRotaImpedeAckELevaOriginalADlqPreservandoObjeto() throws Exception {
        var administrador = new RabbitAdmin(rabbit.getConnectionFactory());
        var binding = new Binding(RabbitConfig.COMPLETED_QUEUE, Binding.DestinationType.QUEUE,
                RabbitConfig.EXCHANGE, RabbitConfig.COMPLETED_ROUTING_KEY, null);
        administrador.removeBinding(binding);
        UUID id = UUID.randomUUID();
        try {
            enviar(Contratos.mensagem("usuarios", id));
            assertThat(rabbit.receive(RabbitConfig.REPORT_GENERATION_DLQ, 15000)).isNotNull();
            verify(publicador, times(3)).publicar(any());
            assertThat(rabbit.receive(RabbitConfig.COMPLETED_QUEUE)).isNull();
            assertThat(minio.statObject(StatObjectArgs.builder().bucket(configuracao.exportBucket())
                    .object("exportacoes/usuarios/" + id + ".csv").build()).size()).isPositive();
        } finally {
            administrador.declareBinding(binding);
        }
    }

    private void enviar(Message mensagem) {
        rabbit.send(RabbitConfig.EXCHANGE, RabbitConfig.REPORT_GENERATION_ROUTING_KEY, mensagem);
    }

    private HttpResponse<byte[]> baixar(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
