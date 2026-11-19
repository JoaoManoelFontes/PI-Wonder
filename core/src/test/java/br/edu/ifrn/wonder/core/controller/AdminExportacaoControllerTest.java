package br.edu.ifrn.wonder.core.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitadaResponse;
import br.edu.ifrn.wonder.core.security.SecurityConfig;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.AdminDashboardService;
import br.edu.ifrn.wonder.core.service.ExportacaoService;
import br.edu.ifrn.wonder.core.service.PrestadorService;
import br.edu.ifrn.wonder.core.service.UsuarioAdminService;

@WebMvcTest({AdminUsuarioController.class, AdminPrestadorController.class, AdminDashboardController.class})
@Import(SecurityConfig.class)
class AdminExportacaoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ExportacaoService exportacaoService;
    @MockitoBean
    private UsuarioAdminService usuarioAdminService;
    @MockitoBean
    private PrestadorService prestadorService;
    @MockitoBean
    private AdminDashboardService adminDashboardService;
    @MockitoBean
    private UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    @MockitoBean
    private OpaqueTokenIntrospector introspectorTokenOpaco;

    @Test
    void deveSolicitarExportacaoDeUsuariosComOsFiltrosDaListagem() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportacaoService.solicitarUsuarios(" Ana ", "CUSTOMER")).thenReturn(resposta(id));

        mockMvc.perform(admin("/admin/usuarios/exportacoes")
                        .param("busca", " Ana ").param("papel", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportacao_id").value(id.toString()))
                .andExpect(jsonPath("$.mensagem").value(
                        "Exportação solicitada. A planilha será enviada para o seu e-mail."));

        verify(exportacaoService).solicitarUsuarios(" Ana ", "CUSTOMER");
        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void deveSolicitarExportacaoDePrestadoresComOsFiltrosDaListagem() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportacaoService.solicitarPrestadores(
                "salão", "ativo", "2026-11-01", "2026-11-30", "mais_antigos"))
                .thenReturn(resposta(id));

        mockMvc.perform(admin("/admin/prestadores/exportacoes")
                        .param("busca", "salão").param("status", "ativo")
                        .param("data_inicio", "2026-11-01").param("data_fim", "2026-11-30")
                        .param("ordem", "mais_antigos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportacao_id").value(id.toString()));

        verify(exportacaoService).solicitarPrestadores(
                "salão", "ativo", "2026-11-01", "2026-11-30", "mais_antigos");
        verifyNoInteractions(prestadorService);
    }

    @Test
    void deveSolicitarExportacaoDeAgendamentosComOMesmoPeriodoDaListagem() throws Exception {
        UUID id = UUID.randomUUID();
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 30);
        when(exportacaoService.solicitarAgendamentos(inicio, fim)).thenReturn(resposta(id));

        mockMvc.perform(admin("/admin/dashboard/agendamentos/exportacoes")
                        .param("data_inicio", "2026-11-01").param("data_fim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportacao_id").value(id.toString()));

        verify(exportacaoService).solicitarAgendamentos(inicio, fim);
        verifyNoInteractions(adminDashboardService);
    }

    @Test
    void deveRejeitarDataInvalidaAntesDePrepararExportacao() throws Exception {
        mockMvc.perform(admin("/admin/dashboard/agendamentos/exportacoes")
                        .param("data_inicio", "01/11/2026").param("data_fim", "2026-11-30"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(exportacaoService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/usuarios/exportacoes",
            "/admin/prestadores/exportacoes",
            "/admin/dashboard/agendamentos/exportacoes"
    })
    void deveRestringirExportacoesAoAdministrador(String rota) throws Exception {
        mockMvc.perform(post(rota)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(rota).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(exportacaoService);
    }

    private ExportacaoSolicitadaResponse resposta(UUID id) {
        return new ExportacaoSolicitadaResponse(id,
                "Exportação solicitada. A planilha será enviada para o seu e-mail.");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder admin(String rota) {
        return post(rota).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
