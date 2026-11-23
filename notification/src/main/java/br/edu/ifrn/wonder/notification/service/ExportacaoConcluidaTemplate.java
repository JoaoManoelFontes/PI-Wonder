package br.edu.ifrn.wonder.notification.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import br.edu.ifrn.wonder.notification.dto.EmailRequest;
import br.edu.ifrn.wonder.notification.dto.ExportacaoConcluida;

@Component
public class ExportacaoConcluidaTemplate {

    private static final DateTimeFormatter FORMATO_VALIDADE = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss XXX").withZone(ZoneId.of("America/Sao_Paulo"));

    private final String modelo;

    public ExportacaoConcluidaTemplate() throws IOException {
        modelo = new ClassPathResource("templates/exportacao_concluida.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public EmailRequest renderizar(ExportacaoConcluida exportacao) {
        Assert.notNull(exportacao, "A exportação é obrigatória");
        Assert.notNull(exportacao.exportacaoId(), "O identificador da exportação é obrigatório");
        Assert.hasText(exportacao.email(), "O destinatário é obrigatório");
        Assert.hasText(exportacao.nome(), "O nome é obrigatório");
        Assert.hasText(exportacao.nomeArquivo(), "O nome do arquivo é obrigatório");
        Assert.hasText(exportacao.arquivoUrl(), "O link de download é obrigatório");
        Assert.notNull(exportacao.expiraEm(), "A validade do link é obrigatória");

        return new EmailRequest(exportacao.email(), "Exportação concluída com sucesso no Wonder",
                modelo.formatted(exportacao.nome(), exportacao.nomeArquivo(), exportacao.arquivoUrl(),
                        FORMATO_VALIDADE.format(exportacao.expiraEm())), null);
    }
}
