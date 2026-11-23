package br.edu.ifrn.wonder.core.service;

import java.time.LocalDate;
import java.util.UUID;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitada;
import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitada.Tipo;
import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitadaResponse;
import br.edu.ifrn.wonder.core.messaging.RabbitProvider;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExportacaoService {

    private final UsuarioAdminService usuarioAdminService;
    private final PrestadorService prestadorService;
    private final AdminDashboardService adminDashboardService;
    private final RabbitProvider rabbitProvider;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

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
        var solicitante = usuarioAdminService.detalhar(usuarioAutenticadoProvider.getUuidUsuario());
        if (solicitante.email() == null || solicitante.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O usuário solicitante não possui e-mail cadastrado no Keycloak");
        }
        String nome = (Objects.toString(solicitante.nome(), "") + " "
                + Objects.toString(solicitante.sobrenome(), "")).trim().replaceAll("\\s+", " ");
        if (nome.isEmpty()) {
            nome = solicitante.username();
        }
        UUID exportacaoId = UUID.randomUUID();
        rabbitProvider.sendReportGenerationJob(new ExportacaoSolicitada(
                exportacaoId, tipo, dados, solicitante.email().trim(), nome));
        return new ExportacaoSolicitadaResponse(exportacaoId,
                "Exportação solicitada. A planilha será enviada para o seu e-mail.");
    }
}
