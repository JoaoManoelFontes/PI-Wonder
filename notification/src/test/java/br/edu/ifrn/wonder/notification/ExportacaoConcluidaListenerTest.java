package br.edu.ifrn.wonder.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

import br.edu.ifrn.wonder.notification.dto.EmailRequest;
import br.edu.ifrn.wonder.notification.dto.ExportacaoConcluida;
import br.edu.ifrn.wonder.notification.service.EmailService;
import br.edu.ifrn.wonder.notification.service.ExportacaoConcluidaTemplate;

class ExportacaoConcluidaListenerTest {

    private final EmailService servicoEmail = mock(EmailService.class);
    private ExportacaoConcluidaListener listener;

    @BeforeEach
    void configurar() throws Exception {
        listener = new ExportacaoConcluidaListener(servicoEmail, new ExportacaoConcluidaTemplate());
    }

    @Test
    void deveEnviarSucessoComLinkOriginalEValidadeEmBrasilia() {
        listener.handle(evento("https://arquivos.example.com/usuarios.csv?assinatura=abc%2F123&x=1"));

        var captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(servicoEmail).enviar(captor.capture());
        var email = captor.getValue();
        assertThat(email.destinatario()).isEqualTo("ana@example.com");
        assertThat(email.assunto()).isEqualTo("Exportação concluída com sucesso no Wonder");
        assertThat(email.texto()).contains("Olá, Ana São José!", "concluída com sucesso", "usuarios.csv",
                "https://arquivos.example.com/usuarios.csv?assinatura=abc%2F123&x=1",
                "20/11/2026 23:30:00 -03:00", "nova exportação");
        assertThat(email.html()).isNull();
    }

    @Test
    void deveRejeitarEventoSemLinkSemEnviarEmail() {
        assertThatThrownBy(() -> listener.handle(evento(" "))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(servicoEmail);
    }

    @Test
    void devePropagarFalhaSmtpParaTratamentoPeloRabbit() {
        var falha = new MailSendException("SMTP indisponível");
        doThrow(falha).when(servicoEmail).enviar(any());
        assertThatThrownBy(() -> listener.handle(evento("https://arquivos.example.com/usuarios.csv")))
                .isSameAs(falha);
    }

    private ExportacaoConcluida evento(String link) {
        return new ExportacaoConcluida(UUID.randomUUID(), "USUARIOS", "ana@example.com", "Ana São José",
                "usuarios.csv", link, Instant.parse("2026-11-21T02:30:00Z"));
    }
}
