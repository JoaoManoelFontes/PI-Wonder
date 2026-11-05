package br.edu.ifrn.wonder.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import br.edu.ifrn.wonder.notification.dto.EmailRequest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender remetenteEmail;
    private EmailService servico;

    @BeforeEach
    void configurar() {
        servico = new EmailService(remetenteEmail, "nao-responda@wonder.local");
    }

    @Test
    void deveEnviarTextoComRemetenteDestinatarioEAssuntoEmUtf8() throws Exception {
        configurarCriacaoMensagem();
        servico.enviar(email("cliente@example.com"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(remetenteEmail).send(captor.capture());
        MimeMessage mensagem = captor.getValue();
        mensagem.saveChanges();
        assertThat(mensagem.getFrom()[0].toString()).isEqualTo("nao-responda@wonder.local");
        assertThat(mensagem.getAllRecipients()).hasSize(1);
        assertThat(mensagem.getAllRecipients()[0].toString()).isEqualTo("cliente@example.com");
        assertThat(mensagem.getSubject()).isEqualTo("Confirmação de agendamento");
        assertThat(mensagem.getContent()).isEqualTo("Olá, seu agendamento foi confirmado.");
        assertThat(mensagem.getContentType()).containsIgnoringCase("text/plain").contains("UTF-8");
    }

    @Test
    void deveEnviarLoteEmUmaUnicaChamadaComDestinatariosSeparados() throws Exception {
        configurarCriacaoMensagem();
        servico.enviarEmLote(List.of(email("ana@example.com"), email("bia@example.com")));

        ArgumentCaptor<MimeMessage[]> captor = ArgumentCaptor.forClass(MimeMessage[].class);
        verify(remetenteEmail).send(captor.capture());
        MimeMessage[] mensagens = captor.getValue();
        assertThat(mensagens).hasSize(2);
        assertThat(mensagens[0].getAllRecipients()).hasSize(1);
        assertThat(mensagens[1].getAllRecipients()).hasSize(1);
        assertThat(mensagens[0].getAllRecipients()[0].toString()).isEqualTo("ana@example.com");
        assertThat(mensagens[1].getAllRecipients()[0].toString()).isEqualTo("bia@example.com");
        verify(remetenteEmail, never()).send(any(MimeMessage.class));
    }

    @Test
    void naoDeveAbrirConexaoParaLoteVazio() {
        servico.enviarEmLote(List.of());
        verifyNoInteractions(remetenteEmail);
    }

    @Test
    void deveRejeitarListaNula() {
        assertThatThrownBy(() -> servico.enviarEmLote(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(remetenteEmail);
    }

    @ParameterizedTest
    @MethodSource("emailsInvalidos")
    void deveRejeitarDadosObrigatoriosInvalidos(EmailRequest email) {
        assertThatThrownBy(() -> servico.enviar(email)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(remetenteEmail);
    }

    static Stream<EmailRequest> emailsInvalidos() {
        return Stream.of(null,
                new EmailRequest(" ", "Assunto", "Texto", null),
                new EmailRequest("ana@example.com", " ", "Texto", null),
                new EmailRequest("ana@example.com", "Assunto\r\nBcc: bia@example.com", "Texto", null),
                new EmailRequest("ana@example.com", "Assunto", " ", "<p>Texto</p>"));
    }

    @Test
    void naoDeveEnviarParteDoLoteQuandoUmaMensagemForInvalida() {
        configurarCriacaoMensagem();
        assertThatThrownBy(() -> servico.enviarEmLote(Arrays.asList(email("ana@example.com"), null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(remetenteEmail, never()).send(any(MimeMessage[].class));
        verify(remetenteEmail, never()).send(any(MimeMessage.class));
    }

    @Test
    void deveRejeitarEnderecoInvalidoAntesDoEnvio() {
        configurarCriacaoMensagem();
        assertThatThrownBy(() -> servico.enviar(email("endereco-invalido")))
                .isInstanceOf(MailParseException.class);
        verify(remetenteEmail, never()).send(any(MimeMessage.class));
    }

    @Test
    void devePropagarFalhaSmtpNoEnvioIndividual() {
        configurarCriacaoMensagem();
        MailSendException falha = new MailSendException("SMTP indisponível");
        doThrow(falha).when(remetenteEmail).send(any(MimeMessage.class));
        assertThatThrownBy(() -> servico.enviar(email("ana@example.com"))).isSameAs(falha);
    }

    @Test
    void devePropagarFalhaSmtpNoLoteSemReenviar() {
        configurarCriacaoMensagem();
        MailSendException falha = new MailSendException("Falha parcial no lote");
        doThrow(falha).when(remetenteEmail).send(any(MimeMessage[].class));
        assertThatThrownBy(() -> servico.enviarEmLote(List.of(email("ana@example.com"))))
                .isSameAs(falha);
        verify(remetenteEmail).send(any(MimeMessage[].class));
    }

    private void configurarCriacaoMensagem() {
        when(remetenteEmail.createMimeMessage())
                .thenAnswer(invocacao -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private EmailRequest email(String destinatario) {
        return new EmailRequest(destinatario, "Confirmação de agendamento",
                "Olá, seu agendamento foi confirmado.", null);
    }
}
