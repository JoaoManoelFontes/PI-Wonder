package br.edu.ifrn.wonder.core.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitada;
import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitada.Tipo;
import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitadaResponse;
import br.edu.ifrn.wonder.core.messaging.RabbitProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExportacaoService {

    private final UsuarioAdminService usuarioAdminService;
    private final PrestadorService prestadorService;
    private final AdminDashboardService adminDashboardService;
    private final RabbitProvider rabbitProvider;

    public ExportacaoSolicitadaResponse solicitarUsuarios(String busca, String papel) {
        var dados = usuarioAdminService.listarParaExportacao(busca, papel);
        return publicar(Tipo.USUARIOS, dados);
    }

    public ExportacaoSolicitadaResponse solicitarPrestadores(String busca, String status,
            String dataInicio, String dataFim, String ordem) {
        var dados = prestadorService.listarAdminParaExportacao(busca, status, dataInicio, dataFim, ordem);
        return publicar(Tipo.PRESTADORES, dados);
    }

    public ExportacaoSolicitadaResponse solicitarAgendamentos(LocalDate dataInicio, LocalDate dataFim) {
        var dados = adminDashboardService.consultarAgendamentos(dataInicio, dataFim);
        return publicar(Tipo.AGENDAMENTOS, dados);
    }

    private ExportacaoSolicitadaResponse publicar(Tipo tipo, Object dados) {
        UUID exportacaoId = UUID.randomUUID();
        rabbitProvider.sendReportGenerationJob(new ExportacaoSolicitada(exportacaoId, tipo, dados));
        return new ExportacaoSolicitadaResponse(exportacaoId,
                "Exportação solicitada. A planilha será enviada para o seu e-mail.");
    }
}
