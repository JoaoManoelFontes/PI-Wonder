package br.edu.ifrn.wonder.core;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

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

@SpringBootTest(properties = "wonder.keycloak.client-secret=secret-teste")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AdminDashboardIntegrationTest.Configuracao.class})
@Transactional
class AdminDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MockRestServiceServer servidorKeycloak;
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

    @BeforeEach
    void preparar() {
        servidorKeycloak.reset();
        // Banco exclusivo do Testcontainers; o rollback restaura o seed ao final do teste.
        jdbcTemplate.execute("TRUNCATE TABLE perfis_usuarios CASCADE");
    }

    @AfterEach
    void verificarChamadasExternas() {
        servidorKeycloak.verify();
    }

    @Test
    void deveContarTodosOsPrestadoresESomenteAtendimentosConcluidosDoMes() throws Exception {
        Prestador prestador = null;
        for (StatusPrestador status : StatusPrestador.values()) {
            Prestador criado = criarPrestador(status);
            if (status == StatusPrestador.ATIVO) {
                prestador = criado;
            }
        }
        Servico servico = criarServico(prestador);
        for (StatusAgendamento status : StatusAgendamento.values()) {
            criarAgendamento(servico, "2026-11-15T09:00", status);
        }
        esperarUsuarios("1248");

        consultar().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.total_usuarios").value(1248))
                .andExpect(jsonPath("$.total_prestadores").value(5))
                .andExpect(jsonPath("$.prestadores_pendentes").value(1))
                .andExpect(jsonPath("$.atendimentos_concluidos_mes").value(1))
                .andExpect(jsonPath("$.mes_referencia").value("2026-11"));
    }

    @Test
    void deveAplicarLimitesLocaisDoMesSemUsarDataDaConclusao() throws Exception {
        Servico servico = criarServico(criarPrestador(StatusPrestador.ATIVO));
        Agendamento anterior = criarAgendamento(servico, "2026-10-31T23:59:59", StatusAgendamento.CONCLUIDO);
        criarAgendamento(servico, "2026-11-01T00:00", StatusAgendamento.CONCLUIDO);
        criarAgendamento(servico, "2026-11-30T23:59:59.999999", StatusAgendamento.CONCLUIDO);
        criarAgendamento(servico, "2026-12-01T00:00", StatusAgendamento.CONCLUIDO);
        agendamentoRepository.flush();
        jdbcTemplate.update("""
                INSERT INTO historico_agendamentos
                    (agendamento_id, usuario_keycloak_id, status_anterior, status_novo, alterado_em)
                VALUES (?, ?, 'CONFIRMADO', 'CONCLUIDO', TIMESTAMP '2026-11-05 10:00:00')
                """, anterior.getId(), UUID.randomUUID());
        esperarUsuarios("4");

        consultar().andExpect(status().isOk())
                .andExpect(jsonPath("$.atendimentos_concluidos_mes").value(2))
                .andExpect(jsonPath("$.mes_referencia").value("2026-11"));
    }

    @Test
    void deveRetornarZerosQuandoNaoHaRegistros() throws Exception {
        esperarUsuarios("0");

        consultar().andExpect(status().isOk())
                .andExpect(jsonPath("$.total_usuarios").value(0))
                .andExpect(jsonPath("$.total_prestadores").value(0))
                .andExpect(jsonPath("$.prestadores_pendentes").value(0))
                .andExpect(jsonPath("$.atendimentos_concluidos_mes").value(0));
    }

    @Test
    void deveRetornarZeroQuandoHaAgendamentosMasNenhumConcluido() throws Exception {
        Servico servico = criarServico(criarPrestador(StatusPrestador.ATIVO));
        criarAgendamento(servico, "2026-11-15T09:00", StatusAgendamento.CONFIRMADO);
        esperarUsuarios("2");

        consultar().andExpect(status().isOk())
                .andExpect(jsonPath("$.atendimentos_concluidos_mes").value(0));
    }

    @Test
    void deveRetirarSolicitacaoDevolvidaDasPendenciasSemAlterarTotal() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        esperarUsuarios("1");
        consultar().andExpect(status().isOk())
                .andExpect(jsonPath("$.total_prestadores").value(1))
                .andExpect(jsonPath("$.prestadores_pendentes").value(1));
        servidorKeycloak.verify();
        servidorKeycloak.reset();
        prestador.setStatus(StatusPrestador.RASCUNHO);
        prestadorRepository.saveAndFlush(prestador);
        esperarUsuarios("1");

        consultar().andExpect(status().isOk())
                .andExpect(jsonPath("$.total_prestadores").value(1))
                .andExpect(jsonPath("$.prestadores_pendentes").value(0));
    }

    @Test
    void deveRejeitarAcessoAnonimoSemConsultarKeycloak() throws Exception {
        mockMvc.perform(get("/admin/dashboard/indicadores"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_PROVIDER"})
    void deveRejeitarAcessoSemAdminSemConsultarKeycloak(String papel) throws Exception {
        mockMvc.perform(get("/admin/dashboard/indicadores")
                        .with(jwt().authorities(new SimpleGrantedAuthority(papel))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "-1", "abc", "1.5"})
    void deveRetornar503SemResumoParcialQuandoContagemForInvalida(String corpo) throws Exception {
        esperarUsuarios(corpo);

        consultar().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.total_usuarios").doesNotExist())
                .andExpect(jsonPath("$.total_prestadores").doesNotExist());
    }

    @Test
    void deveRetornar503QuandoKeycloakFalhar() throws Exception {
        esperarToken();
        servidorKeycloak.expect(requestTo("http://keycloak.test/admin/realms/wonder/users/count"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        consultar().andExpect(status().isServiceUnavailable());
    }

    @Test
    void deveRetornar503QuandoKeycloakExcederTimeout() throws Exception {
        esperarToken();
        servidorKeycloak.expect(requestTo("http://keycloak.test/admin/realms/wonder/users/count"))
                .andRespond(withException(new SocketTimeoutException("Timeout simulado")));

        consultar().andExpect(status().isServiceUnavailable());
    }

    private ResultActions consultar() throws Exception {
        return mockMvc.perform(get("/admin/dashboard/indicadores")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void esperarToken() {
        servidorKeycloak.expect(requestTo("http://keycloak.test/realms/wonder/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"token-teste\"}", MediaType.APPLICATION_JSON));
    }

    private void esperarUsuarios(String corpo) {
        esperarToken();
        servidorKeycloak.expect(requestTo("http://keycloak.test/admin/realms/wonder/users/count"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-teste"))
                .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
    }

    private Prestador criarPrestador(StatusPrestador status) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.saveAndFlush(Prestador.builder().perfilUsuario(perfil)
                .nomeEstab("Studio teste " + status).status(status)
                .ativo(status == StatusPrestador.ATIVO).build());
    }

    private Servico criarServico(Prestador prestador) {
        return servicoRepository.saveAndFlush(Servico.builder().prestador(prestador)
                .nome("Corte").preco(new BigDecimal("50.00")).duracaoMin(60).build());
    }

    private Agendamento criarAgendamento(Servico servico, String inicio, StatusAgendamento status) {
        LocalDateTime horario = LocalDateTime.parse(inicio);
        return agendamentoRepository.saveAndFlush(Agendamento.builder()
                .cliente(servico.getPrestador().getPerfilUsuario())
                .prestador(servico.getPrestador()).servico(servico)
                .inicio(horario).fim(horario.plusHours(1)).status(status).build());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuracao {

        private final RestClient.Builder construtor = RestClient.builder().baseUrl("http://keycloak.test");
        private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(construtor).build();

        @Bean
        MockRestServiceServer servidorKeycloak() {
            return servidor;
        }

        @Bean
        @Primary
        RestClient clienteKeycloakSimulado() {
            return construtor.build();
        }

        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), ZoneOffset.UTC);
        }
    }
}
