package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.edu.ifrn.wonder.core.dto.AgendamentosTotaisStatusResponse;
import br.edu.ifrn.wonder.core.dto.DashboardAgendamentosResponse;
import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitada;
import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitada.Tipo;
import br.edu.ifrn.wonder.core.dto.PrestadoresAdminExportacaoResponse;
import br.edu.ifrn.wonder.core.dto.UsuariosAdminExportacaoResponse;
import br.edu.ifrn.wonder.core.messaging.RabbitProvider;

class ExportacaoServiceTest {

    private final UsuarioAdminService usuarioAdminService = mock(UsuarioAdminService.class);
    private final PrestadorService prestadorService = mock(PrestadorService.class);
    private final AdminDashboardService adminDashboardService = mock(AdminDashboardService.class);
    private final RabbitProvider rabbitProvider = mock(RabbitProvider.class);
    private final ExportacaoService service = new ExportacaoService(
            usuarioAdminService, prestadorService, adminDashboardService, rabbitProvider);

    @Test
    void deveBuscarTodosUsuariosEPublicarComando() {
        var dados = new UsuariosAdminExportacaoResponse(List.of());
        when(usuarioAdminService.listarParaExportacao("Ana", "CUSTOMER")).thenReturn(dados);

        var resposta = service.solicitarUsuarios("Ana", "CUSTOMER");

        ExportacaoSolicitada comando = capturarComando();
        assertThat(comando.exportacaoId()).isEqualTo(resposta.exportacaoId());
        assertThat(comando.tipo()).isEqualTo(Tipo.USUARIOS);
        assertThat(comando.dados()).isSameAs(dados);
        verify(usuarioAdminService).listarParaExportacao("Ana", "CUSTOMER");
    }

    @Test
    void deveBuscarTodosPrestadoresEPublicarComando() {
        var dados = new PrestadoresAdminExportacaoResponse(List.of());
        when(prestadorService.listarAdminParaExportacao("", "ativo", null, null, "mais_recentes"))
                .thenReturn(dados);

        var resposta = service.solicitarPrestadores("", "ativo", null, null, "mais_recentes");

        ExportacaoSolicitada comando = capturarComando();
        assertThat(comando.exportacaoId()).isEqualTo(resposta.exportacaoId());
        assertThat(comando.tipo()).isEqualTo(Tipo.PRESTADORES);
        assertThat(comando.dados()).isSameAs(dados);
    }

    @Test
    void deveBuscarAgendamentosNoDashboardEPublicarComando() {
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 30);
        var totais = new AgendamentosTotaisStatusResponse(1, 2, 3, 4);
        var dados = new DashboardAgendamentosResponse(
                inicio, fim, 10, new BigDecimal("0.33"), totais, List.of());
        when(adminDashboardService.consultarAgendamentos(inicio, fim)).thenReturn(dados);

        var resposta = service.solicitarAgendamentos(inicio, fim);

        ExportacaoSolicitada comando = capturarComando();
        assertThat(comando.exportacaoId()).isEqualTo(resposta.exportacaoId());
        assertThat(comando.tipo()).isEqualTo(Tipo.AGENDAMENTOS);
        assertThat(comando.dados()).isSameAs(dados);
        verify(adminDashboardService).consultarAgendamentos(inicio, fim);
    }

    private ExportacaoSolicitada capturarComando() {
        var captor = ArgumentCaptor.forClass(ExportacaoSolicitada.class);
        verify(rabbitProvider).sendReportGenerationJob(captor.capture());
        return captor.getValue();
    }
}
