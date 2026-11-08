package br.edu.ifrn.wonder.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

import br.edu.ifrn.wonder.notification.dto.EmailRequest;
import br.edu.ifrn.wonder.notification.dto.ProviderRequestReviewed;
import br.edu.ifrn.wonder.notification.dto.ProviderRequestReviewed.Resultado;
import br.edu.ifrn.wonder.notification.service.EmailService;
import br.edu.ifrn.wonder.notification.service.ProviderRequestReviewedTemplate;

class ProviderRequestReviewedListenerTest {

    private final EmailService servicoEmail = mock(EmailService.class);
    private ProviderRequestReviewedListener listener;

    @BeforeEach
    void configurar() throws Exception {
        listener = new ProviderRequestReviewedListener(servicoEmail, new ProviderRequestReviewedTemplate());
    }

    @Test
    void deveEnviarAprovacaoSemExibirMotivoDeRejeicao() {
        listener.handle(new ProviderRequestReviewed(42L, "ana@example.com", "Salão São José",
                Resultado.APROVADO, "Motivo antigo"));

        var captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(servicoEmail).enviar(captor.capture());
        var email = captor.getValue();
        assertThat(email.destinatario()).isEqualTo("ana@example.com");
        assertThat(email.assunto()).isEqualTo("Solicitação de prestador aprovada no Wonder");
        assertThat(email.texto()).contains("Salão São José", "42", "foi aprovada", "aplicativo Wonder")
                .doesNotContain("Motivo antigo", "recusada");
        assertThat(email.html()).isNull();
    }

    @Test
    void deveEnviarRecusaComMotivoDinamico() {
        listener.handle(new ProviderRequestReviewed(73L, "bia@example.com", "Espaço Beleza",
                Resultado.REJEITADO, "Informe o endereço completo."));

        var captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(servicoEmail).enviar(captor.capture());
        var email = captor.getValue();
        assertThat(email.destinatario()).isEqualTo("bia@example.com");
        assertThat(email.assunto()).isEqualTo("Solicitação de prestador recusada no Wonder");
        assertThat(email.texto()).contains("Espaço Beleza", "73", "foi recusada",
                "Informe o endereço completo.", "enviá-lo novamente").doesNotContain("foi aprovada");
        assertThat(email.html()).isNull();
    }

    @ParameterizedTest
    @MethodSource("mensagensInvalidas")
    void deveRejeitarMensagemInvalidaSemEnviarEmail(ProviderRequestReviewed solicitacao) {
        assertThatThrownBy(() -> listener.handle(solicitacao)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(servicoEmail);
    }

    static Stream<ProviderRequestReviewed> mensagensInvalidas() {
        return Stream.of(null,
                new ProviderRequestReviewed(null, "ana@example.com", "Salão", Resultado.APROVADO, null),
                new ProviderRequestReviewed(0L, "ana@example.com", "Salão", Resultado.APROVADO, null),
                new ProviderRequestReviewed(42L, null, "Salão", Resultado.APROVADO, null),
                new ProviderRequestReviewed(42L, " ", "Salão", Resultado.APROVADO, null),
                new ProviderRequestReviewed(42L, "ana@example.com", null, Resultado.APROVADO, null),
                new ProviderRequestReviewed(42L, "ana@example.com", " ", Resultado.APROVADO, null),
                new ProviderRequestReviewed(42L, "ana@example.com", "Salão", null, null),
                new ProviderRequestReviewed(42L, "ana@example.com", "Salão", Resultado.REJEITADO, null),
                new ProviderRequestReviewed(42L, "ana@example.com", "Salão", Resultado.REJEITADO, " "));
    }

    @Test
    void devePropagarFalhaSmtpParaTratamentoPeloRabbit() {
        var falha = new MailSendException("SMTP indisponível");
        doThrow(falha).when(servicoEmail).enviar(any());
        assertThatThrownBy(() -> listener.handle(new ProviderRequestReviewed(42L, "ana@example.com",
                "Salão", Resultado.APROVADO, null))).isSameAs(falha);
    }
}
