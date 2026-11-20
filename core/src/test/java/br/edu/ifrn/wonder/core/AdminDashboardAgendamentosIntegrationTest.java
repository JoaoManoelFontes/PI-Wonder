package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import br.edu.ifrn.wonder.core.service.KeycloakAdminService;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AdminDashboardAgendamentosIntegrationTest.Configuracao.class})
@Transactional
class AdminDashboardAgendamentosIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @MockitoBean
    private KeycloakAdminService keycloakAdminService;

    private Servico servico;

    @BeforeEach
    void preparar() {
        // Banco exclusivo do Testcontainers; o rollback restaura o seed.
        jdbcTemplate.execute("TRUNCATE TABLE perfis_usuarios CASCADE");
        var perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        var prestador = prestadorRepository.saveAndFlush(Prestador.builder().perfilUsuario(perfil)
                .nomeEstab("Studio teste").status(StatusPrestador.ATIVO).ativo(true).build());
        servico = servicoRepository.saveAndFlush(Servico.builder().prestador(prestador)
                .nome("Corte").preco(new BigDecimal("50.00")).duracaoMin(60).build());
    }

    @Test
    void deveConsultarQuatroStatusEPreencherDiasVaziosComResumoConsistente() throws Exception {
        for (StatusAgendamento status : StatusAgendamento.values()) {
            criarAgendamento("2026-11-01T09:00", status);
        }
        criarAgendamento("2026-11-03T09:00", StatusAgendamento.CONCLUIDO);

        var resultado = consultar("2026-11-01", "2026-11-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$.total_agendamentos").value(5))
                .andExpect(jsonPath("$.media_por_dia").value(1.67))
                .andExpect(jsonPath("$.totais_por_status.pendente").value(1))
                .andExpect(jsonPath("$.totais_por_status.confirmado").value(1))
                .andExpect(jsonPath("$.totais_por_status.cancelado").value(1))
                .andExpect(jsonPath("$.totais_por_status.concluido").value(2))
                .andExpect(jsonPath("$.dias.length()").value(3))
                .andExpect(jsonPath("$.dias[0].data").value("2026-11-01"))
                .andExpect(jsonPath("$.dias[0].total_agendamentos").value(4))
                .andExpect(jsonPath("$.dias[1].data").value("2026-11-02"))
                .andExpect(jsonPath("$.dias[1].total_agendamentos").value(0))
                .andExpect(jsonPath("$.dias[1].totais_por_status.pendente").value(0))
                .andExpect(jsonPath("$.dias[1].totais_por_status.confirmado").value(0))
                .andExpect(jsonPath("$.dias[1].totais_por_status.cancelado").value(0))
                .andExpect(jsonPath("$.dias[1].totais_por_status.concluido").value(0))
                .andExpect(jsonPath("$.dias[2].data").value("2026-11-03"))
                .andExpect(jsonPath("$.dias[2].total_agendamentos").value(1))
                .andReturn();

        var resposta = jsonMapper.readTree(resultado.getResponse().getContentAsString());
        long totalDias = 0;
        for (var dia : resposta.get("dias")) {
            assertThat(dia.size()).isEqualTo(3);
            long totalStatus = 0;
            for (String status : new String[]{"pendente", "confirmado", "cancelado", "concluido"}) {
                totalStatus += dia.get("totais_por_status").get(status).asLong();
            }
            assertThat(dia.get("total_agendamentos").asLong()).isEqualTo(totalStatus);
            totalDias += totalStatus;
        }
        assertThat(resposta.get("total_agendamentos").asLong()).isEqualTo(totalDias);
        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void deveAplicarLimitesLocaisSemConverterParaUtcOuContarPeloFim() throws Exception {
        criarAgendamento("2026-10-31T23:59:59.999999", StatusAgendamento.CONCLUIDO);
        criarAgendamento("2026-11-01T00:00", StatusAgendamento.PENDENTE);
        criarAgendamento("2026-11-30T23:59:59.999999", StatusAgendamento.CANCELADO);
        criarAgendamento("2026-12-01T00:00", StatusAgendamento.CONCLUIDO);

        consultar("2026-11-01", "2026-11-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_agendamentos").value(2))
                .andExpect(jsonPath("$.dias[0].totais_por_status.pendente").value(1))
                .andExpect(jsonPath("$.dias[29].totais_por_status.cancelado").value(1))
                .andExpect(jsonPath("$.totais_por_status.concluido").value(0));
    }

    @Test
    void deveMapearProjecaoNativaComDataStatusECountLongNoPostgres() {
        for (StatusAgendamento status : StatusAgendamento.values()) {
            criarAgendamento("2026-11-13T09:00", status);
        }
        criarAgendamento("2026-11-13T15:00", StatusAgendamento.CONCLUIDO);
        criarAgendamento("2026-11-14T00:00", StatusAgendamento.CONCLUIDO);

        var contagens = agendamentoRepository.contarPorDiaEStatus(
                LocalDate.of(2026, 11, 13).atStartOfDay(), LocalDate.of(2026, 11, 14).atStartOfDay());

        assertThat(contagens).hasSize(4).allSatisfy(contagem -> {
            assertThat(contagem.getData()).isEqualTo(LocalDate.of(2026, 11, 13));
            assertThat(StatusAgendamento.valueOf(contagem.getStatus())).isNotNull();
            assertThat(contagem.getQuantidade()).isEqualTo(contagem.getStatus().equals("CONCLUIDO") ? 2L : 1L);
        });
    }

    @Test
    void deveContarStatusAtualPeloInicioMesmoQuandoConclusaoAconteceEmOutroMes() throws Exception {
        var agendamento = criarAgendamento("2026-10-31T23:30", StatusAgendamento.CONCLUIDO);
        jdbcTemplate.update("""
                INSERT INTO historico_agendamentos
                    (agendamento_id, usuario_keycloak_id, status_anterior, status_novo, alterado_em)
                VALUES (?, ?, 'CONFIRMADO', 'CONCLUIDO', TIMESTAMP '2026-11-05 10:00:00')
                """, agendamento.getId(), UUID.randomUUID());

        consultar("2026-10-31", "2026-10-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_agendamentos").value(1))
                .andExpect(jsonPath("$.totais_por_status.concluido").value(1));
        consultar("2026-11-01", "2026-11-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_agendamentos").value(0));
    }

    @Test
    void deveUsarMesCompletoDeSaoPauloNaViradaUtcEReconciliarComCard() throws Exception {
        criarAgendamento("2026-11-01T00:00", StatusAgendamento.CONCLUIDO);
        criarAgendamento("2026-11-30T23:59:59.999999", StatusAgendamento.CONCLUIDO);
        criarAgendamento("2026-11-13T09:00", StatusAgendamento.PENDENTE);
        criarAgendamento("2026-12-01T00:00", StatusAgendamento.CONCLUIDO);

        var resultado = mockMvc.perform(administrador())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data_inicio").value("2026-11-01"))
                .andExpect(jsonPath("$.data_fim").value("2026-11-30"))
                .andExpect(jsonPath("$.dias.length()").value(30))
                .andExpect(jsonPath("$.total_agendamentos").value(3))
                .andExpect(jsonPath("$.media_por_dia").value(0.10))
                .andReturn();
        long concluidos = jsonMapper.readTree(resultado.getResponse().getContentAsString())
                .get("totais_por_status").get("concluido").asLong();
        verifyNoInteractions(keycloakAdminService);
        when(keycloakAdminService.contarUsuarios()).thenReturn(1L);

        mockMvc.perform(get("/admin/dashboard/indicadores")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes_referencia").value("2026-11"))
                .andExpect(jsonPath("$.atendimentos_concluidos_mes").value(concluidos));
        verify(keycloakAdminService).contarUsuarios();
        verifyNoMoreInteractions(keycloakAdminService);
    }

    @Test
    void deveRetornarTodosOsDiasZeradosQuandoPeriodoNaoPossuiRegistros() throws Exception {
        consultar("2026-11-01", "2026-11-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_agendamentos").value(0))
                .andExpect(jsonPath("$.media_por_dia").value(0.0))
                .andExpect(jsonPath("$.dias.length()").value(3))
                .andExpect(jsonPath("$.dias[2].data").value("2026-11-03"))
                .andExpect(jsonPath("$.dias[2].total_agendamentos").value(0));
        verifyNoInteractions(keycloakAdminService);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-12-31, 2027-01-02, 2027-01-01T09:00",
            "2028-02-28, 2028-03-01, 2028-02-29T09:00"
    })
    void deveTratarIntervalosQueAtravessamAnoEMesBissexto(String inicio, String fim, String atendimento)
            throws Exception {
        criarAgendamento(atendimento, StatusAgendamento.CONFIRMADO);

        consultar(inicio, fim)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias.length()").value(3))
                .andExpect(jsonPath("$.dias[1].data").value(atendimento.substring(0, 10)))
                .andExpect(jsonPath("$.dias[1].totais_por_status.confirmado").value(1))
                .andExpect(jsonPath("$.media_por_dia").value(0.33));
    }

    @Test
    void deveRejeitarIntervaloInvertido() throws Exception {
        consultar("2026-11-14", "2026-11-13").andExpect(status().isBadRequest());
        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void deveRejeitarAcessoAnonimo() throws Exception {
        mockMvc.perform(get("/admin/dashboard/agendamentos")).andExpect(status().isUnauthorized());
        verifyNoInteractions(keycloakAdminService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_PROVIDER"})
    void deveRejeitarUsuarioSemAdmin(String papel) throws Exception {
        mockMvc.perform(get("/admin/dashboard/agendamentos")
                        .with(jwt().authorities(new SimpleGrantedAuthority(papel))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(keycloakAdminService);
    }

    private ResultActions consultar(String inicio, String fim) throws Exception {
        return mockMvc.perform(administrador().param("data_inicio", inicio).param("data_fim", fim));
    }

    private MockHttpServletRequestBuilder administrador() {
        return get("/admin/dashboard/agendamentos")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Agendamento criarAgendamento(String inicio, StatusAgendamento status) {
        LocalDateTime horario = LocalDateTime.parse(inicio);
        return agendamentoRepository.saveAndFlush(Agendamento.builder()
                .cliente(servico.getPrestador().getPerfilUsuario())
                .prestador(servico.getPrestador()).servico(servico)
                .inicio(horario).fim(horario.plusHours(1)).status(status).build());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuracao {

        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), ZoneOffset.UTC);
        }
    }
}
