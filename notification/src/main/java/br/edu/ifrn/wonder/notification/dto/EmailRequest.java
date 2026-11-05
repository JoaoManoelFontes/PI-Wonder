package br.edu.ifrn.wonder.notification.dto;

/** Conteúdo já renderizado; HTML opcional, com texto simples como alternativa. */
public record EmailRequest(String destinatario, String assunto, String texto, String html) {
}
