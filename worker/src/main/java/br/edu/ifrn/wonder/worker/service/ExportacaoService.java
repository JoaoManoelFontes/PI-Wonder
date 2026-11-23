package br.edu.ifrn.wonder.worker.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import br.edu.ifrn.wonder.worker.dto.ExportacaoSolicitada;
import br.edu.ifrn.wonder.worker.messaging.ExportacaoPublisher;

@Service
public class ExportacaoService {
    private static final Logger log = LoggerFactory.getLogger(ExportacaoService.class);
    private final ContratoExportacaoService contrato;
    private final GeradorCsvService gerador;
    private final ArmazenamentoExportacaoService armazenamento;
    private final ExportacaoPublisher publicador;

    public ExportacaoService(ContratoExportacaoService contrato, GeradorCsvService gerador,
            ArmazenamentoExportacaoService armazenamento, ExportacaoPublisher publicador) {
        this.contrato = contrato;
        this.gerador = gerador;
        this.armazenamento = armazenamento;
        this.publicador = publicador;
    }

    public void processar(ExportacaoSolicitada pedido) throws Exception {
        Object snapshot = contrato.snapshot(pedido);
        String nomeArquivo = gerador.nomeArquivo(pedido, snapshot);
        var arquivo = Files.createTempFile("wonder-exportacao-", ".csv");
        String etapa = "geracao";
        try {
            try (var destino = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
                gerador.escrever(destino, pedido.tipo(), snapshot);
            }
            etapa = "armazenamento-assinatura";
            var evento = armazenamento.armazenar(pedido, nomeArquivo, arquivo);
            etapa = "publicacao";
            publicador.publicar(evento);
            log.info("Exportação id={} tipo={} etapa=concluida", pedido.exportacaoId(), pedido.tipo());
        } catch (Exception erro) {
            log.warn("Exportação id={} tipo={} etapa={} causa={}", pedido.exportacaoId(), pedido.tipo(), etapa,
                    erro.getClass().getSimpleName());
            throw erro;
        } finally {
            Files.deleteIfExists(arquivo);
        }
    }
}
