package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminListagensIntegrationTest {
    private static final String SECRET = UUID.randomUUID().toString();
    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
            .withEnv("KEYCLOAK_CLIENT_SECRET", SECRET)
            .withCopyFileToContainer(MountableFile.forClasspathResource("keycloak/wonder-realm.json"),
                    "/opt/keycloak/data/import/wonder-realm.json")
            .withCommand("start-dev", "--import-realm").withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/wonder/.well-known/openid-configuration").forPort(8080))
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("wonder.keycloak.server-url", AdminListagensIntegrationTest::endereco);
        registro.add("wonder.keycloak.client-secret", () -> SECRET);
    }

    @Autowired MockMvc mvc;
    @Autowired PrestadorRepository prestadores;
    @Autowired PerfilUsuarioRepository perfis;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean KeycloakClient keycloak;
    final List<UUID> contas = new ArrayList<>();
    String token;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE perfis_usuarios CASCADE");
        token = keycloak.obterToken();
    }

    @AfterEach
    void limpar() {
        for (UUID id : contas) {
            http().delete().uri("/admin/realms/wonder/users/{id}", id)
                    .headers(cabecalhos -> cabecalhos.setBearerAuth(token)).retrieve().toBodilessEntity();
        }
    }

    @Test
    void deveCombinarEstabelecimentoOuResponsavelStatusPeriodoETotalAntesDoRecorte() throws Exception {
        UUID ana = criarConta("Ana", "Silva");
        UUID outro = criarConta("Bia", "Souza");
        var porNome = criarPrestador(outro, "ANA Studio", StatusPrestador.PENDENTE, "2026-11-01T03:00:00Z");
        var porResponsavel = criarPrestador(ana, "Salão Central", StatusPrestador.PENDENTE, "2026-11-01T03:00:00Z");
        criarPrestador(criarConta("Ana", "Maria"), "Ana duplicado", StatusPrestador.SUSPENSO, "2026-11-01T04:00:00Z");
        criarPrestador(criarConta("Ana", "Nova"), "Outubro", StatusPrestador.PENDENTE, "2026-10-31T12:00:00Z");
        // Envio em novembro não torna a criação de outubro elegível.
        jdbc.update("UPDATE prestadores SET enviado_em = ? WHERE nome_estab = 'Outubro'",
                LocalDateTime.parse("2026-11-01T10:00:00"));
        mvc.perform(admin("/admin/prestadores").param("busca", " Ana ").param("status", "pendente")
                        .param("data_inicio", "2026-11-01").param("data_fim", "2026-11-01")
                        .param("inicio", "1").param("limite", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.prestadores[0].id").value(porNome.getId()))
                .andExpect(jsonPath("$.temProxima").value(false));
        mvc.perform(admin("/admin/prestadores").param("busca", "Ana").param("status", "pendente")
                        .param("data_inicio", "2026-11-01").param("data_fim", "2026-11-01")
                        .param("ordem", "mais_antigos").param("inicio", "1").param("limite", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prestadores[0].id").value(porResponsavel.getId()));
    }

    @Test
    void deveUsarLimitesDeSaoPauloETodosStatusIncluindoRascunhoSemEnvio() throws Exception {
        int indice = 0;
        for (StatusPrestador estado : StatusPrestador.values()) {
            criarPrestador(criarConta("Pessoa", ""), "Estado " + indice, estado,
                    indice++ == 0 ? "2026-11-01T03:00:00Z" : "2026-11-02T02:59:59.999999Z");
        }
        criarPrestador(UUID.randomUUID(), "Antes", StatusPrestador.RASCUNHO, "2026-11-01T02:59:59.999999Z");
        criarPrestador(UUID.randomUUID(), "Depois", StatusPrestador.RASCUNHO, "2026-11-02T03:00:00Z");
        mvc.perform(admin("/admin/prestadores").param("data_inicio", "2026-11-01").param("data_fim", "2026-11-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.prestadores.length()").value(5));
        for (StatusPrestador estado : StatusPrestador.values()) {
            mvc.perform(admin("/admin/prestadores").param("status", estado.name().toLowerCase())
                            .param("data_inicio", "2026-11-01").param("data_fim", "2026-11-01"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        }
        mvc.perform(admin("/admin/prestadores")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(7));
    }

    @Test
    void devePaginarComOffsetArbitrarioEPreservarTotalAlemDoFim() throws Exception {
        for (int indice = 0; indice < 25; indice++) {
            criarPrestador(UUID.randomUUID(), "Estab " + indice, StatusPrestador.RASCUNHO, "2026-11-01T03:00:00Z");
        }
        mvc.perform(admin("/admin/prestadores").param("inicio", "23").param("limite", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.prestadores.length()").value(2)).andExpect(jsonPath("$.temProxima").value(false));
        mvc.perform(admin("/admin/prestadores").param("inicio", "40"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.prestadores.length()").value(0)).andExpect(jsonPath("$.temProxima").value(false));
        mvc.perform(admin("/admin/prestadores").param("busca", "inexistente"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void devePreservarContratoEOrdemPorEnvioDosPendentesSemConsultarNomes() throws Exception {
        var posterior = criarPrestador(UUID.randomUUID(), "Primeiro criado", StatusPrestador.PENDENTE, "2026-10-01T03:00:00Z");
        var anterior = criarPrestador(UUID.randomUUID(), "Segundo criado", StatusPrestador.PENDENTE, "2026-11-01T03:00:00Z");
        jdbc.update("UPDATE prestadores SET enviado_em = ? WHERE id = ?", LocalDateTime.parse("2026-11-02T10:00:00"), posterior.getId());
        jdbc.update("UPDATE prestadores SET enviado_em = ? WHERE id = ?", LocalDateTime.parse("2026-11-01T10:00:00"), anterior.getId());
        clearInvocations(keycloak);
        mvc.perform(admin("/admin/prestadores/pendentes")).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(anterior.getId()))
                .andExpect(jsonPath("$[1].id").value(posterior.getId()))
                .andExpect(jsonPath("$[0].enviado_em").value("2026-11-01T10:00:00"))
                .andExpect(jsonPath("$[0].responsavel_nome").doesNotExist());
        verifyNoInteractions(keycloak);
    }

    @Test
    void deveResolverNomeAtualFallbackEContaRemovidaSemTransacaoHttp() throws Exception {
        UUID id = criarConta("Antigo", "Silva");
        var prestador = criarPrestador(id, "Estabelecimento", StatusPrestador.RASCUNHO, "2026-11-01T03:00:00Z");
        keycloak.atualizarUsuario(token, id, Map.of("firstName", "Maria", "lastName", "  Nova  Silva  "));
        doAnswer(chamada -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return chamada.callRealMethod();
        }).when(keycloak).buscarUsuario(anyString(), any());
        mvc.perform(admin("/admin/prestadores"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prestadores[0].length()").value(7))
                .andExpect(jsonPath("$.prestadores[0].responsavel_nome").value("Maria Nova Silva"))
                .andExpect(jsonPath("$.prestadores[0].usuario_id").value(id.toString()))
                .andExpect(jsonPath("$.prestadores[0].criado_em").value("2026-11-01T03:00:00Z"));
        verify(keycloak).buscarUsuario(anyString(), eq(id));
        keycloak.atualizarUsuario(token, id, Map.of("firstName", "", "lastName", ""));
        String username = keycloak.buscarUsuario(token, id).get("username").toString();
        mvc.perform(admin("/admin/prestadores")).andExpect(status().isOk())
                .andExpect(jsonPath("$.prestadores[0].responsavel_nome").value(username));
        http().delete().uri("/admin/realms/wonder/users/{id}", id)
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token)).retrieve().toBodilessEntity();
        contas.remove(id);
        mvc.perform(admin("/admin/prestadores")).andExpect(status().isOk())
                .andExpect(jsonPath("$.prestadores[0].id").value(prestador.getId()))
                .andExpect(jsonPath("$.prestadores[0].responsavel_nome").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void deveManterBuscaLiteralDeEstabelecimentoQuandoNaoHaResponsavelCorrespondente() throws Exception {
        criarPrestador(UUID.randomUUID(), "Studio 100%_!", StatusPrestador.SUSPENSO, "2026-11-01T03:00:00Z");
        criarPrestador(UUID.randomUUID(), "Studio 100abc", StatusPrestador.SUSPENSO, "2026-11-01T03:00:00Z");
        mvc.perform(admin("/admin/prestadores").param("busca", "100%_!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/admin/usuarios|papel|", "/admin/usuarios|papel|TODOS", "/admin/usuarios|inicio|-1",
            "/admin/usuarios|limite|101", "/admin/usuarios|limite|0",
            "/admin/prestadores|status|", "/admin/prestadores|status|aprovado",
            "/admin/prestadores|ordem|", "/admin/prestadores|ordem|desc",
            "/admin/prestadores|inicio|-1", "/admin/prestadores|limite|0", "/admin/prestadores|limite|101",
            "/admin/prestadores|data_inicio|2026-11-01", "/admin/prestadores|data_fim|2026-11-01",
            "/admin/prestadores|data_inicio|"
    }, delimiter = '|')
    void deveRejeitarFiltrosInvalidosAntesDeConsultarFontes(String rota, String parametro, String valor) throws Exception {
        clearInvocations(keycloak);
        mvc.perform(admin(rota).param(parametro, valor == null ? "" : valor)).andExpect(status().isBadRequest());
        verifyNoInteractions(keycloak);
    }

    @ParameterizedTest
    @CsvSource({"2026-02-30,2026-03-01", "2026-11-02,2026-11-01", "2026-1-01,2026-01-02", " ,2026-01-01"})
    void deveRejeitarIntervalosInvalidos(String de, String ate) throws Exception {
        mvc.perform(admin("/admin/prestadores").param("data_inicio", de == null ? "" : de).param("data_fim", ate))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveProtegerAmbasRotasEValidarBuscaLonga() throws Exception {
        for (String rota : List.of("/admin/usuarios", "/admin/prestadores")) {
            mvc.perform(get(rota)).andExpect(status().isUnauthorized());
            for (String papel : List.of("CUSTOMER", "PROVIDER")) {
                mvc.perform(get(rota).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + papel))))
                        .andExpect(status().isForbidden());
            }
            mvc.perform(admin(rota).param("busca", "a".repeat(256))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void deveRetornar503EmFalhaDePaginaMembrosBuscaOuNomeSemSucessoParcial() throws Exception {
        doThrow(new ResourceAccessException("falha de página")).when(keycloak).listarUsuarios(anyString(), anyString(), eq(100), eq(100));
        var candidatos = new ArrayList<Map<String, Object>>();
        for (int indice = 0; indice < 100; indice++) {
            candidatos.add(Map.of("id", UUID.randomUUID().toString(), "username", "nome" + indice));
        }
        doReturn(candidatos).when(keycloak).listarUsuarios(anyString(), anyString(), eq(0), eq(100));
        mvc.perform(admin("/admin/usuarios")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.usuarios").doesNotExist());
        mvc.perform(admin("/admin/prestadores").param("busca", "Ana")).andExpect(status().isServiceUnavailable());
        doReturn(List.of()).when(keycloak).listarUsuarios(anyString(), anyString(), anyInt(), anyInt());
        doReturn(candidatos).when(keycloak).listarMembrosPapel(anyString(), anyString(), eq(0), eq(100));
        doThrow(new ResourceAccessException("falha de membros")).when(keycloak).listarMembrosPapel(anyString(), anyString(), eq(100), eq(100));
        mvc.perform(admin("/admin/usuarios").param("papel", "ADMIN")).andExpect(status().isServiceUnavailable());
        criarPrestador(UUID.randomUUID(), "Nome", StatusPrestador.RASCUNHO, "2026-11-01T03:00:00Z");
        doThrow(new ResourceAccessException("falha de nome")).when(keycloak).buscarUsuario(anyString(), any());
        mvc.perform(admin("/admin/prestadores")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.prestadores").doesNotExist());
    }

    private UUID criarConta(String nome, String sobrenome) {
        var resposta = http().post().uri("/admin/realms/wonder/users")
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                .body(Map.of("username", "listagem-" + UUID.randomUUID(), "firstName", nome,
                        "lastName", sobrenome, "enabled", true)).retrieve().toBodilessEntity();
        String localizacao = resposta.getHeaders().getLocation().toString();
        UUID id = UUID.fromString(localizacao.substring(localizacao.lastIndexOf('/') + 1));
        contas.add(id);
        return id;
    }

    private Prestador criarPrestador(UUID usuarioId, String nome, StatusPrestador estado, String criadoEm) {
        var perfil = perfis.saveAndFlush(new PerfilUsuario(usuarioId));
        var prestador = prestadores.saveAndFlush(Prestador.builder().perfilUsuario(perfil)
                .nomeEstab(nome).status(estado).ativo(false).build());
        jdbc.update("UPDATE prestadores SET criado_em = ? WHERE id = ?", java.sql.Timestamp.from(Instant.parse(criadoEm)), prestador.getId());
        return prestador;
    }

    private MockHttpServletRequestBuilder admin(String rota) {
        return get(rota).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private RestClient http() {
        return RestClient.create(endereco());
    }

    private static String endereco() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }
}
