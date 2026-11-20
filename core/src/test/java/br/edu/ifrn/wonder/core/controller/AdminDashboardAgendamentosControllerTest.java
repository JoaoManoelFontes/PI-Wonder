package br.edu.ifrn.wonder.core.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.security.SecurityConfig;
import br.edu.ifrn.wonder.core.service.AdminDashboardService;
import br.edu.ifrn.wonder.core.service.KeycloakAdminService;

@WebMvcTest(AdminDashboardController.class)
@Import({SecurityConfig.class, AdminDashboardService.class})
class AdminDashboardAgendamentosControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AgendamentoRepository agendamentoRepository;
    @MockitoBean
    private PrestadorRepository prestadorRepository;
    @MockitoBean
    private KeycloakAdminService keycloakAdminService;
    @MockitoBean
    private Clock relogio;
    @MockitoBean
    private OpaqueTokenIntrospector introspectorTokenOpaco;

    @Test
    void deveRetornarContratoCompletoParaPeriodoExplicitoSemDadosPessoais() throws Exception {
        mockMvc.perform(administrador().param("data_inicio", "2026-11-13").param("data_fim", "2026-11-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$.data_inicio").value("2026-11-13"))
                .andExpect(jsonPath("$.data_fim").value("2026-11-13"))
                .andExpect(jsonPath("$.total_agendamentos").value(0))
                .andExpect(jsonPath("$.media_por_dia").value(0.0))
                .andExpect(jsonPath("$.totais_por_status.length()").value(4))
                .andExpect(jsonPath("$.totais_por_status.pendente").value(0))
                .andExpect(jsonPath("$.totais_por_status.confirmado").value(0))
                .andExpect(jsonPath("$.totais_por_status.cancelado").value(0))
                .andExpect(jsonPath("$.totais_por_status.concluido").value(0))
                .andExpect(jsonPath("$.dias.length()").value(1))
                .andExpect(jsonPath("$.dias[0].length()").value(3))
                .andExpect(jsonPath("$.dias[0].data").value("2026-11-13"))
                .andExpect(jsonPath("$.dias[0].total_agendamentos").value(0))
                .andExpect(jsonPath("$.dias[0].totais_por_status.length()").value(4))
                .andExpect(jsonPath("$.dias[0].totais_por_status.pendente").value(0))
                .andExpect(jsonPath("$.dias[0].totais_por_status.confirmado").value(0))
                .andExpect(jsonPath("$.dias[0].totais_por_status.cancelado").value(0))
                .andExpect(jsonPath("$.dias[0].totais_por_status.concluido").value(0));

        verify(agendamentoRepository).contarPorDiaEStatus(
                LocalDate.of(2026, 11, 13).atStartOfDay(), LocalDate.of(2026, 11, 14).atStartOfDay());
        verifyNoInteractions(keycloakAdminService, prestadorRepository, relogio);
    }

    @Test
    void deveUsarMesAtualDeSaoPauloQuandoParametrosEstaoAusentes() throws Exception {
        when(relogio.withZone(ZoneId.of("America/Sao_Paulo")))
                .thenReturn(Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), ZoneOffset.UTC)
                        .withZone(ZoneId.of("America/Sao_Paulo")));

        mockMvc.perform(administrador())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data_inicio").value("2026-11-01"))
                .andExpect(jsonPath("$.data_fim").value("2026-11-30"))
                .andExpect(jsonPath("$.dias.length()").value(30));
        verifyNoInteractions(keycloakAdminService, prestadorRepository);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL, 2026-11-13", "2026-11-13, NULL", "2026-11-14, 2026-11-13",
            "'', ''", "'', 2026-11-13", "2026-11-13, ''",
            "2026-02-30, 2026-03-01", "2026-11-01, 2026-11-31",
            "2026-2-01, 2026-02-28", "13/11/2026, 2026-11-13",
            "2026-11-13T00:00:00, 2026-11-13", "abc, 2026-11-13",
            "+999999999-12-31, +999999999-12-31", "0000-01-01, 0000-01-02"
    }, nullValues = "NULL")
    void deveRejeitarParametrosInvalidosSemConsultarBanco(String inicio, String fim) throws Exception {
        var requisicao = administrador();
        if (inicio != null) {
            requisicao.param("data_inicio", inicio);
        }
        if (fim != null) {
            requisicao.param("data_fim", fim);
        }

        mockMvc.perform(requisicao).andExpect(status().isBadRequest());
        verifyNoInteractions(agendamentoRepository, keycloakAdminService, prestadorRepository);
    }

    @Test
    void deveRejeitarAcessoAnonimoAntesDeConsultarFontes() throws Exception {
        mockMvc.perform(get("/admin/dashboard/agendamentos")).andExpect(status().isUnauthorized());
        verifyNoInteractions(agendamentoRepository, keycloakAdminService, prestadorRepository, relogio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_PROVIDER", "SCOPE_openid"})
    void deveRejeitarAcessoSemAdminAntesDeConsultarFontes(String papel) throws Exception {
        mockMvc.perform(get("/admin/dashboard/agendamentos")
                        .with(jwt().authorities(new SimpleGrantedAuthority(papel))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(agendamentoRepository, keycloakAdminService, prestadorRepository, relogio);
    }

    private MockHttpServletRequestBuilder administrador() {
        return get("/admin/dashboard/agendamentos")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
