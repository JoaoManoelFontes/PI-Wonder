package br.edu.ifrn.wonder.notification.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import br.edu.ifrn.wonder.notification.dto.EmailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender remetenteEmail;
    private final String enderecoRemetente;

    public EmailService(JavaMailSender remetenteEmail,
            @Value("${wonder.email.remetente}") String enderecoRemetente) {
        this.remetenteEmail = remetenteEmail;
        this.enderecoRemetente = enderecoRemetente;
    }

    public void enviar(EmailRequest email) {
        remetenteEmail.send(prepararMensagem(email));
    }

    /** Envia mensagens separadas reutilizando a conexão SMTP do JavaMailSender. */
    public void enviarEmLote(List<EmailRequest> emails) {
        Assert.notNull(emails, "A lista de emails é obrigatória");
        if (emails.isEmpty()) {
            return;
        }
        MimeMessage[] mensagens = emails.stream()
                .map(this::prepararMensagem)
                .toArray(MimeMessage[]::new);
        remetenteEmail.send(mensagens);
    }

    private MimeMessage prepararMensagem(EmailRequest email) {
        Assert.notNull(email, "O email é obrigatório");
        Assert.hasText(email.destinatario(), "O destinatário é obrigatório");
        Assert.hasText(email.assunto(), "O assunto é obrigatório");
        Assert.isTrue(!email.assunto().contains("\r") && !email.assunto().contains("\n"),
                "O assunto não pode conter quebras de linha");
        Assert.hasText(email.texto(), "O texto simples é obrigatório");

        boolean possuiHtml = StringUtils.hasText(email.html());
        MimeMessage mensagem = remetenteEmail.createMimeMessage();
        try {
            MimeMessageHelper auxiliar = new MimeMessageHelper(mensagem, possuiHtml,
                    StandardCharsets.UTF_8.name());
            auxiliar.setValidateAddresses(true);
            auxiliar.setFrom(enderecoRemetente);
            auxiliar.setTo(email.destinatario());
            auxiliar.setSubject(email.assunto());
            if (possuiHtml) {
                auxiliar.setText(email.texto(), email.html());
            } else {
                auxiliar.setText(email.texto());
            }
            return mensagem;
        } catch (MessagingException excecao) {
            throw new MailParseException("Não foi possível preparar o email", excecao);
        }
    }
}
