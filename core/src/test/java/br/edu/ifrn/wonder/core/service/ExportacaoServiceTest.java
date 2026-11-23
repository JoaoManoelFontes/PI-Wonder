package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.dto.AgendamentosTotaisStatusResponse;
import br.edu.ifrn.wonder.core.dto.DashboardAgendamentosResponse;
import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitada;
import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitada.Tipo;
import br.edu.ifrn.wonder.core.dto.PrestadoresAdminExportacaoResponse;
import br.edu.ifrn.wonder.core.dto.UsuariosAdminExportacaoResponse;
import br.edu.ifrn.wonder.core.dto.UsuarioAdminResponse;
import br.edu.ifrn.wonder.core.messaging.RabbitProvider;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;

class ExportacaoServiceTest {

    private final UsuarioAdminService usuarioAdminService = mock(UsuarioAdminService.class);
    private final PrestadorService prestadorService = mock(PrestadorService.class);
    private final AdminDashboardService adminDashboardService = mock(AdminDashboardService.class);
    private final RabbitProvider rabbitProvider = mock(RabbitProvider.class);
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider = mock(UsuarioAutenticadoProvider.class);
    private final UUID solicitanteId = UUID.randomUUID();
    private final ExportacaoService service = new ExportacaoService(
            usuarioAdminService, prestadorService, adminDashboardService, rabbitProvider, usuarioAutenticadoProvider);

    @BeforeEach
    void prepararSolicitante() {
        when(usuarioAutenticadoProvider.getUuidUsuario()).thenReturn(solicitanteId);
        when(usuarioAdminService.detalhar(solicitanteId)).thenReturn(new UsuarioAdminResponse(
                solicitanteId, "ana", " Ana ", " Silva ", " ana@example.com ", true));
    }

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

    @Test
    void deveUsarUsernameQuandoSolicitanteNaoPossuiNome() {
        when(usuarioAdminService.detalhar(solicitanteId)).thenReturn(new UsuarioAdminResponse(
                solicitanteId, "ana", null, " ", "ana@example.com", true));

        service.solicitarUsuarios("", null);

        var captor = ArgumentCaptor.forClass(ExportacaoSolicitada.class);
        verify(rabbitProvider).sendReportGenerationJob(captor.capture());
        assertThat(captor.getValue().nome()).isEqualTo("ana");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void naoDevePublicarQuandoSolicitanteNaoPossuiEmail(String email) {
        when(usuarioAdminService.detalhar(solicitanteId)).thenReturn(new UsuarioAdminResponse(
                solicitanteId, "ana", "Ana", "Silva", email, true));

        assertThatThrownBy(() -> service.solicitarUsuarios("", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        excecao -> assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(rabbitProvider);
    }

    @Test
    void naoDevePublicarQuandoKeycloakFalharAoConsultarSolicitante() {
        when(usuarioAdminService.detalhar(solicitanteId)).thenThrow(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.solicitarUsuarios("", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        excecao -> assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(rabbitProvider);
    }

    private ExportacaoSolicitada capturarComando() {
        var captor = ArgumentCaptor.forClass(ExportacaoSolicitada.class);
        verify(rabbitProvider).sendReportGenerationJob(captor.capture());
        var comando = captor.getValue();
        assertThat(comando.email()).isEqualTo("ana@example.com");
        assertThat(comando.nome()).isEqualTo("Ana Silva");
        verify(usuarioAdminService).detalhar(solicitanteId);
        return comando;
    }
}
