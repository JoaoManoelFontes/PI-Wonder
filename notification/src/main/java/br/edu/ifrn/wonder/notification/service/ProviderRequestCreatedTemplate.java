package br.edu.ifrn.wonder.notification.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import br.edu.ifrn.wonder.notification.dto.EmailRequest;
import br.edu.ifrn.wonder.notification.dto.ProviderRequestCreated;

@Component
public class ProviderRequestCreatedTemplate {

    private final String modelo;

    public ProviderRequestCreatedTemplate() throws IOException {
        modelo = new ClassPathResource("templates/provider_request_created.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public List<EmailRequest> renderizar(ProviderRequestCreated solicitacao) {
        Assert.notNull(solicitacao, "A solicitação é obrigatória");
        Assert.notEmpty(solicitacao.destinatarios(), "Informe ao menos um destinatário");
        solicitacao.destinatarios().forEach(destinatario ->
                Assert.hasText(destinatario, "O destinatário é obrigatório"));
        var prestador = solicitacao.prestador();
        Assert.notNull(prestador, "Os dados do prestador são obrigatórios");
        Assert.isTrue(prestador.id() != null && prestador.id() > 0, "O identificador do prestador é obrigatório");
        Assert.hasText(prestador.nomeEstab(), "O nome do estabelecimento é obrigatório");
        Assert.hasText(prestador.cidade(), "A cidade é obrigatória");
        Assert.hasText(prestador.estado(), "O estado é obrigatório");

        String documento = StringUtils.hasText(prestador.documento()) ? prestador.documento() : "Não informado";
        String texto = modelo.formatted(prestador.nomeEstab(), prestador.id(), documento,
                prestador.cidade(), prestador.estado());
        return solicitacao.destinatarios().stream().distinct()
                .map(destinatario -> new EmailRequest(destinatario,
                        "Nova solicitação de prestador no Wonder", texto, null))
                .toList();
    }
}
