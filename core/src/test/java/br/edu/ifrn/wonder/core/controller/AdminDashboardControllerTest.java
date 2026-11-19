package br.edu.ifrn.wonder.core.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import br.edu.ifrn.wonder.core.dto.DashboardIndicadoresResponse;
import br.edu.ifrn.wonder.core.security.SecurityConfig;
import br.edu.ifrn.wonder.core.service.AdminDashboardService;
import br.edu.ifrn.wonder.core.service.ExportacaoService;

@WebMvcTest(AdminDashboardController.class)
@Import(SecurityConfig.class)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AdminDashboardService adminDashboardService;
    @MockitoBean
    private ExportacaoService exportacaoService;
    @MockitoBean
    private OpaqueTokenIntrospector introspectorTokenOpaco;

    @Test
    void deveRetornarContratoAdministrativoComNumerosBrutos() throws Exception {
        when(adminDashboardService.consultarIndicadores())
                .thenReturn(new DashboardIndicadoresResponse(1248, 86, 7, 342, "2026-11"));

        mockMvc.perform(get("/admin/dashboard/indicadores")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.total_usuarios").value(1248))
                .andExpect(jsonPath("$.total_prestadores").value(86))
                .andExpect(jsonPath("$.prestadores_pendentes").value(7))
                .andExpect(jsonPath("$.atendimentos_concluidos_mes").value(342))
                .andExpect(jsonPath("$.mes_referencia").value("2026-11"));
    }

    @Test
    void deveRejeitarRequisicaoSemAutenticacaoAntesDeConsultarFontes() throws Exception {
        mockMvc.perform(get("/admin/dashboard/indicadores"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminDashboardService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_PROVIDER", "SCOPE_openid"})
    void deveRejeitarUsuarioSemAdminAntesDeConsultarFontes(String autoridade) throws Exception {
        mockMvc.perform(get("/admin/dashboard/indicadores")
                        .with(jwt().authorities(new SimpleGrantedAuthority(autoridade))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminDashboardService);
    }
}
