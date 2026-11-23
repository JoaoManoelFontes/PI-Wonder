package br.edu.ifrn.wonder.worker.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import br.edu.ifrn.wonder.worker.service.ContratoExportacaoService;
import br.edu.ifrn.wonder.worker.service.ExportacaoService;
import br.edu.ifrn.wonder.worker.service.SolicitacaoInvalidaException;

@Component
public class ReportGenerationListener {
    private final ContratoExportacaoService contrato;
    private final ExportacaoService exportacao;

    public ReportGenerationListener(ContratoExportacaoService contrato, ExportacaoService exportacao) {
        this.contrato = contrato;
        this.exportacao = exportacao;
    }

    @RabbitListener(queues = RabbitConfig.REPORT_GENERATION_QUEUE)
    public void handle(Message mensagem) {
        // Ler apenas o corpo: headers de classes do core não participam da conversão.
        final br.edu.ifrn.wonder.worker.dto.ExportacaoSolicitada pedido;
        try {
            pedido = contrato.ler(mensagem.getBody());
        } catch (SolicitacaoInvalidaException erro) {
            org.slf4j.LoggerFactory.getLogger(ReportGenerationListener.class)
                    .warn("Exportação id=indisponivel tipo=indisponivel etapa=validacao causa=SolicitacaoInvalidaException");
            throw new AmqpRejectAndDontRequeueException("Solicitação inválida; encaminhada à DLQ");
        }
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            try {
                exportacao.processar(pedido);
                return;
            } catch (Exception erro) {
                if (tentativa == 3) break;
                try {
                    Thread.sleep(tentativa * 1000L);
                } catch (InterruptedException interrupcao) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new AmqpRejectAndDontRequeueException("Tentativas de exportação esgotadas; encaminhada à DLQ");
    }
}
