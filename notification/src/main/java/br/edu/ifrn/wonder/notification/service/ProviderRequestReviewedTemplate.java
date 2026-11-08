package br.edu.ifrn.wonder.notification.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import br.edu.ifrn.wonder.notification.dto.EmailRequest;
import br.edu.ifrn.wonder.notification.dto.ProviderRequestReviewed;

@Component
public class ProviderRequestReviewedTemplate {

    private final String modeloAprovado;
    private final String modeloRejeitado;

    public ProviderRequestReviewedTemplate() throws IOException {
        modeloAprovado = new ClassPathResource("templates/provider_request_approved.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        modeloRejeitado = new ClassPathResource("templates/provider_request_rejected.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public EmailRequest renderizar(ProviderRequestReviewed solicitacao) {
        Assert.notNull(solicitacao, "A solicitação é obrigatória");
        Assert.isTrue(solicitacao.prestadorId() != null && solicitacao.prestadorId() > 0,
                "O identificador do prestador é obrigatório");
        Assert.hasText(solicitacao.destinatario(), "O destinatário é obrigatório");
        Assert.hasText(solicitacao.nomeEstabelecimento(), "O nome do estabelecimento é obrigatório");
        Assert.notNull(solicitacao.resultado(), "O resultado da análise é obrigatório");

        return switch (solicitacao.resultado()) {
            case APROVADO -> new EmailRequest(solicitacao.destinatario(),
                    "Solicitação de prestador aprovada no Wonder",
                    modeloAprovado.formatted(solicitacao.nomeEstabelecimento(), solicitacao.prestadorId()), null);
            case REJEITADO -> {
                Assert.hasText(solicitacao.motivoRejeicao(), "O motivo da rejeição é obrigatório");
                yield new EmailRequest(solicitacao.destinatario(),
                        "Solicitação de prestador recusada no Wonder",
                        modeloRejeitado.formatted(solicitacao.nomeEstabelecimento(), solicitacao.prestadorId(),
                                solicitacao.motivoRejeicao()), null);
            }
        };
    }
}
