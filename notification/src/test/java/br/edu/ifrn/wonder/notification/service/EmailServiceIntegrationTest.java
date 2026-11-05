package br.edu.ifrn.wonder.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import br.edu.ifrn.wonder.notification.NotificationApplication;
import br.edu.ifrn.wonder.notification.RabbitConfig;

import br.edu.ifrn.wonder.notification.dto.EmailRequest;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(classes = NotificationApplication.class)
class EmailServiceIntegrationTest {

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));

    @Container
    static final GenericContainer<?> mailhog = new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:v1.0.1"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/").forPort(8025));

    @DynamicPropertySource
    static void configurarSmtp(DynamicPropertyRegistry registro) {
        registro.add("spring.rabbitmq.host", rabbitmq::getHost);
        registro.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registro.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registro.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
        registro.add("spring.mail.host", mailhog::getHost);
        registro.add("spring.mail.port", () -> mailhog.getMappedPort(1025));
        registro.add("spring.mail.username", () -> "");
        registro.add("spring.mail.password", () -> "");
        registro.add("spring.mail.properties[mail.smtp.auth]", () -> false);
        registro.add("spring.mail.properties[mail.smtp.starttls.enable]", () -> false);
        registro.add("spring.mail.properties[mail.smtp.starttls.required]", () -> false);
        registro.add("wonder.email.remetente", () -> "nao-responda@wonder.local");
    }

    @Autowired
    private EmailService servico;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void limparCaixaDeEntrada() throws Exception {
        consultar("/api/v1/messages", "DELETE");
    }

    @Test
    void deveEntregarTextoPorSmtpComAcentuacao() throws Exception {
        servico.enviar(new EmailRequest("ana@example.com", "Confirmação de serviço",
                "Olá, seu horário está confirmado!", null));

        List<MimeMessage> mensagens = mensagensRecebidas();
        assertThat(mensagens).hasSize(1);
        MimeMessage mensagem = mensagens.getFirst();
        assertThat(mensagem.getFrom()[0].toString()).isEqualTo("nao-responda@wonder.local");
        assertThat(mensagem.getAllRecipients()[0].toString()).isEqualTo("ana@example.com");
        assertThat(mensagem.getSubject()).isEqualTo("Confirmação de serviço");
        assertThat(mensagem.isMimeType("text/plain")).isTrue();
        assertThat(mensagem.getContent()).isEqualTo("Olá, seu horário está confirmado!");
    }

    @Test
    void deveEntregarHtmlComAlternativaEmTextoSimples() throws Exception {
        servico.enviar(new EmailRequest("ana@example.com", "Confirmação",
                "Olá, Ana!", "<h1>Olá, Ana!</h1>"));

        List<MimeMessage> mensagens = mensagensRecebidas();
        assertThat(mensagens).hasSize(1);
        Multipart alternativas = encontrarAlternativas(mensagens.getFirst());
        assertThat(alternativas).isNotNull();
        assertThat(alternativas.getCount()).isEqualTo(2);
        assertThat(alternativas.getBodyPart(0).isMimeType("text/plain")).isTrue();
        assertThat(alternativas.getBodyPart(0).getContent()).isEqualTo("Olá, Ana!");
        assertThat(alternativas.getBodyPart(1).isMimeType("text/html")).isTrue();
        assertThat(alternativas.getBodyPart(1).getContent()).isEqualTo("<h1>Olá, Ana!</h1>");
    }

    @Test
    void deveEntregarLoteSemExporOutrosDestinatarios() throws Exception {
        servico.enviarEmLote(List.of(
                new EmailRequest("ana@example.com", "Para Ana", "Olá, Ana!", null),
                new EmailRequest("bia@example.com", "Para Bia", "Olá, Bia!", "<p>Olá, Bia!</p>")));

        List<MimeMessage> mensagens = mensagensRecebidas();
        assertThat(mensagens).hasSize(2);
        List<String> destinatarios = new ArrayList<>();
        for (MimeMessage mensagem : mensagens) {
            assertThat(mensagem.getAllRecipients()).hasSize(1);
            String destinatario = mensagem.getAllRecipients()[0].toString();
            destinatarios.add(destinatario);
            assertThat(mensagem.getSubject()).isEqualTo(destinatario.startsWith("ana") ? "Para Ana" : "Para Bia");
        }
        assertThat(destinatarios).containsExactlyInAnyOrder("ana@example.com", "bia@example.com");
    }

    @Test
    void deveConsumirJsonDoCoreEEntregarEmailsPorSmtp() throws Exception {
        // O tipo do producer pertence a outro módulo; o consumer deve inferir seu próprio DTO.
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType("application/json");
        propriedades.setHeader("__TypeId__", "br.edu.ifrn.wonder.core.dto.ProviderRequestCreated");
        String json = """
                {"destinatarios":["ana@example.com","bia@example.com"],
                 "prestador":{"id":42,"nomeEstab":"Salão São José","documento":"12345678900",
                              "cidade":"Natal","estado":"RN"}}
                """;
        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.PROVIDER_REQUEST_CREATED_ROUTING_KEY,
                new Message(json.getBytes(StandardCharsets.UTF_8), propriedades));

        List<MimeMessage> mensagens = List.of();
        long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (mensagens.size() < 2 && System.nanoTime() < limite) {
            mensagens = mensagensRecebidas();
            if (mensagens.size() < 2) {
                Thread.sleep(100);
            }
        }
        assertThat(mensagens).hasSize(2);
        List<String> destinatarios = new ArrayList<>();
        for (MimeMessage mensagem : mensagens) {
            assertThat(mensagem.getAllRecipients()).hasSize(1);
            destinatarios.add(mensagem.getAllRecipients()[0].toString());
            assertThat(mensagem.getSubject()).isEqualTo("Nova solicitação de prestador no Wonder");
            assertThat(mensagem.getContent().toString()).contains("Salão São José", "Natal/RN",
                    "aplicativo Wonder", "aprovar ou rejeitar");
        }
        assertThat(destinatarios).containsExactlyInAnyOrder("ana@example.com", "bia@example.com");
    }

    @Test
    void deveEncaminharSolicitacaoInvalidaParaDlqSemEnviarEmail() throws Exception {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType("application/json");
        String json = """
                {"destinatarios":[],"prestador":null}
                """;
        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.PROVIDER_REQUEST_CREATED_ROUTING_KEY,
                new Message(json.getBytes(StandardCharsets.UTF_8), propriedades));

        Message rejeitada = rabbitTemplate.receive(RabbitConfig.PROVIDER_REQUEST_CREATED_DLQ, 15000);
        assertThat(rejeitada).isNotNull();
        assertThat(new String(rejeitada.getBody(), StandardCharsets.UTF_8)).isEqualTo(json);
        assertThat(mensagensRecebidas()).isEmpty();
    }

    private List<MimeMessage> mensagensRecebidas() throws Exception {
        var mensagensJson = json.readTree(consultar("/api/v1/messages", "GET"));
        List<MimeMessage> mensagens = new ArrayList<>();
        for (var mensagemJson : mensagensJson) {
            String identificador = mensagemJson.path("ID").asString();
            String conteudo = consultar("/api/v1/messages/" + identificador + "/download", "GET");
            mensagens.add(new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(conteudo.getBytes(StandardCharsets.UTF_8))));
        }
        return mensagens;
    }

    private String consultar(String caminho, String metodo) throws Exception {
        URI endereco = URI.create("http://" + mailhog.getHost() + ":" + mailhog.getMappedPort(8025) + caminho);
        HttpRequest requisicao = HttpRequest.newBuilder(endereco).timeout(Duration.ofSeconds(5))
                .method(metodo, HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(resposta.statusCode()).isEqualTo(200);
        return resposta.body();
    }

    private Multipart encontrarAlternativas(Part parte) throws Exception {
        if (parte.isMimeType("multipart/alternative")) {
            return (Multipart) parte.getContent();
        }
        if (parte.isMimeType("multipart/*")) {
            Multipart conteudo = (Multipart) parte.getContent();
            for (int indice = 0; indice < conteudo.getCount(); indice++) {
                Multipart alternativas = encontrarAlternativas(conteudo.getBodyPart(indice));
                if (alternativas != null) {
                    return alternativas;
                }
            }
        }
        return null;
    }

}
