package br.edu.ifrn.wonder.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

import br.edu.ifrn.wonder.notification.dto.EmailRequest;
import br.edu.ifrn.wonder.notification.dto.ProviderRequestCreated;
import br.edu.ifrn.wonder.notification.dto.ProviderRequestCreated.DadosPrestador;
import br.edu.ifrn.wonder.notification.service.EmailService;
import br.edu.ifrn.wonder.notification.service.ProviderRequestCreatedTemplate;

class NotificationListenerTest {

    private final EmailService servicoEmail = mock(EmailService.class);
    private NotificationListener listener;

    @BeforeEach
    void configurar() throws Exception {
        listener = new NotificationListener(servicoEmail, new ProviderRequestCreatedTemplate());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deveEnviarTemplateParaCadaDestinatarioSemDuplicar() {
        listener.handle(new ProviderRequestCreated(
                List.of("ana@example.com", "bia@example.com", "ana@example.com"), prestador()));

        ArgumentCaptor<List<EmailRequest>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(servicoEmail).enviarEmLote(captor.capture());
        assertThat(captor.getValue()).extracting(EmailRequest::destinatario)
                .containsExactly("ana@example.com", "bia@example.com");
        assertThat(captor.getValue()).allSatisfy(email -> {
            assertThat(email.assunto()).isEqualTo("Nova solicitação de prestador no Wonder");
            assertThat(email.texto()).contains("nova solicitação", "Salão São José", "42", "12345678900",
                    "Natal/RN", "aplicativo Wonder", "aprovar ou rejeitar");
            assertThat(email.html()).isNull();
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deveEnviarEmailComDocumentoNaoInformadoQuandoAusenteOuEmBranco(String documento) {
        listener.handle(new ProviderRequestCreated(List.of("ana@example.com"),
                new DadosPrestador(42L, "Salão São José", documento, "Natal", "RN")));

        ArgumentCaptor<List<EmailRequest>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(servicoEmail).enviarEmLote(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(email -> {
            assertThat(email.destinatario()).isEqualTo("ana@example.com");
            assertThat(email.texto()).contains("Documento: Não informado", "Salão São José", "Natal/RN");
        });
    }

    @ParameterizedTest
    @MethodSource("solicitacoesInvalidas")
    void deveRejeitarMensagemInvalidaSemEnviarEmails(ProviderRequestCreated solicitacao) {
        assertThatThrownBy(() -> listener.handle(solicitacao)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(servicoEmail);
    }

    static Stream<ProviderRequestCreated> solicitacoesInvalidas() {
        return Stream.of(null,
                new ProviderRequestCreated(null, prestador()),
                new ProviderRequestCreated(List.of(), prestador()),
                new ProviderRequestCreated(Arrays.asList("ana@example.com", null), prestador()),
                new ProviderRequestCreated(List.of(" "), prestador()),
                new ProviderRequestCreated(List.of("ana@example.com"), null),
                new ProviderRequestCreated(List.of("ana@example.com"), new DadosPrestador(null, "Salão", "123", "Natal", "RN")),
                new ProviderRequestCreated(List.of("ana@example.com"), new DadosPrestador(42L, " ", "123", "Natal", "RN")));
    }

    @Test
    void devePropagarFalhaSmtpParaRejeicaoPeloListenerRabbit() {
        MailSendException falha = new MailSendException("SMTP indisponível");
        doThrow(falha).when(servicoEmail).enviarEmLote(anyList());
        assertThatThrownBy(() -> listener.handle(
                new ProviderRequestCreated(List.of("ana@example.com"), prestador()))).isSameAs(falha);
        verify(servicoEmail, times(1)).enviarEmLote(anyList());
    }

    static DadosPrestador prestador() {
        return new DadosPrestador(42L, "Salão São José", "12345678900", "Natal", "RN");
    }
}
