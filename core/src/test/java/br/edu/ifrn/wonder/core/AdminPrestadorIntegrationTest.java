package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.dto.KeycloakRoleResponse;
import br.edu.ifrn.wonder.core.service.KeycloakAdminService;

@Testcontainers
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminPrestadorIntegrationTest {

    private static final String SECRET = UUID.randomUUID().toString();

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
            DockerImageName.parse("quay.io/keycloak/keycloak:26.0"))
            .withEnv("KEYCLOAK_CLIENT_SECRET", SECRET)
            .withCopyFileToContainer(MountableFile.forClasspathResource("keycloak/wonder-realm.json"),
                    "/opt/keycloak/data/import/wonder-realm.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/wonder/.well-known/openid-configuration").forPort(8080))
            .withStartupTimeout(Duration.ofMinutes(2));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @MockitoSpyBean
    private KeycloakClient keycloakClient;
    @Autowired
    private KeycloakAdminService keycloakAdminService;

    private Prestador prestadorCriado;
    private UUID usuarioCriado;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("wonder.keycloak.server-url", AdminPrestadorIntegrationTest::enderecoKeycloak);
        registro.add("wonder.keycloak.realm", () -> "wonder");
        registro.add("wonder.keycloak.client-id", () -> "wonder-core");
        registro.add("wonder.keycloak.client-secret", () -> SECRET);
    }

    @AfterEach
    void limparDados() {
        if (prestadorCriado != null) {
            prestadorRepository.deleteById(prestadorCriado.getId());
            perfilUsuarioRepository.deleteById(prestadorCriado.getPerfilUsuario().getId());
        }
        if (usuarioCriado != null) {
            clienteAdministrativo().delete().uri("/admin/realms/wonder/users/{id}", usuarioCriado)
                    .retrieve().toBodilessEntity();
        }
    }

    @ParameterizedTest
    @CsvSource({"detalhar,PENDENTE,false", "aprovar,ATIVO,true", "reprovar,REJEITADO,false"})
    void deveResolverIdDaRotaEExecutarOperacaoAdministrativa(
            String operacao, StatusPrestador statusEsperado, boolean ativoEsperado) throws Exception {
        UUID administradorId = UUID.randomUUID();
        UUID usuarioId = criarUsuarioKeycloak();
        Prestador prestador = criarPrestador(usuarioId, StatusPrestador.PENDENTE);
        assertThat(rolesDoUsuario()).contains("CUSTOMER").doesNotContain("PROVIDER");
        String caminho = "/admin/prestadores/" + prestador.getId();
        var requisicao = "detalhar".equals(operacao) ? get(caminho)
                : patch(caminho + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("aprovar".equals(operacao) ? "{\"status\":\"ativo\"}"
                                : "{\"status\":\"rejeitado\",\"motivo_rejeicao\":\"Cadastro inconsistente\"}");

        mockMvc.perform(requisicao.with(jwt()
                        .jwt(token -> token.subject(administradorId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$.status").value(statusEsperado.name().toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.ativo").value(ativoEsperado));

        Prestador salvo = prestadorRepository.findById(prestador.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(statusEsperado);
        assertThat(salvo.isAtivo()).isEqualTo(ativoEsperado);
        if (!"detalhar".equals(operacao)) {
            assertThat(salvo.getAprovadoPor()).isEqualTo(administradorId.toString());
        }
        if ("aprovar".equals(operacao)) {
            assertThat(salvo.getAprovadoEm()).isNotNull();
            assertThat(rolesDoUsuario()).contains("CUSTOMER", "PROVIDER").doesNotContain("ADMIN");
            mockMvc.perform(comoAdministrador(requisicaoAprovacao(prestador.getId()), administradorId))
                    .andExpect(status().isConflict());
            assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getAprovadoEm())
                    .isEqualTo(salvo.getAprovadoEm());
        } else {
            assertThat(rolesDoUsuario()).contains("CUSTOMER").doesNotContain("PROVIDER");
        }
    }

    @Test
    void deveRetornar503EPreservarBancoQuandoKeycloakNaoConseguirAtribuirRole() throws Exception {
        Prestador prestador = criarPrestador(UUID.randomUUID(), StatusPrestador.PENDENTE);

        mockMvc.perform(comoAdministrador(requisicaoAprovacao(prestador.getId()), UUID.randomUUID()))
                .andExpect(status().isServiceUnavailable());

        Prestador salvo = prestadorRepository.findById(prestador.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
        assertThat(salvo.isAtivo()).isFalse();
        assertThat(salvo.getAprovadoEm()).isNull();
        assertThat(salvo.getAprovadoPor()).isNull();
    }

    @Test
    void deveAprovarQuandoUsuarioJaPossuiProvider() throws Exception {
        UUID usuarioId = criarUsuarioKeycloak();
        Prestador prestador = criarPrestador(usuarioId, StatusPrestador.PENDENTE);
        keycloakAdminService.adicionarRoleProvider(usuarioId);

        mockMvc.perform(comoAdministrador(requisicaoAprovacao(prestador.getId()), UUID.randomUUID()))
                .andExpect(status().isOk());

        assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPrestador.ATIVO);
        assertThat(rolesDoUsuario()).contains("CUSTOMER", "PROVIDER");
    }

    @Test
    void devePermitirNovaTentativaSeRespostaSePerderDepoisDaAtribuicaoNoKeycloak() throws Exception {
        UUID usuarioId = criarUsuarioKeycloak();
        Prestador prestador = criarPrestador(usuarioId, StatusPrestador.PENDENTE);
        doAnswer(invocacao -> {
            invocacao.callRealMethod();
            throw new ResourceAccessException("Resposta da atribuição não recebida");
        }).when(keycloakClient).adicionarRole(eq(usuarioId), anyString(), any(KeycloakRoleResponse.class));

        mockMvc.perform(comoAdministrador(requisicaoAprovacao(prestador.getId()), UUID.randomUUID()))
                .andExpect(status().isServiceUnavailable());

        assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPrestador.PENDENTE);
        assertThat(rolesDoUsuario()).contains("CUSTOMER", "PROVIDER");

        doCallRealMethod().when(keycloakClient)
                .adicionarRole(eq(usuarioId), anyString(), any(KeycloakRoleResponse.class));

        mockMvc.perform(comoAdministrador(requisicaoAprovacao(prestador.getId()), UUID.randomUUID()))
                .andExpect(status().isOk());

        assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPrestador.ATIVO);
        assertThat(rolesDoUsuario()).contains("CUSTOMER", "PROVIDER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "PROVIDER"})
    void deveRecusarAprovacaoSemRoleAdmin(String role) throws Exception {
        Prestador prestador = criarPrestador(criarUsuarioKeycloak(), StatusPrestador.PENDENTE);

        mockMvc.perform(requisicaoAprovacao(prestador.getId()).with(jwt()
                        .jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isForbidden());

        assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPrestador.PENDENTE);
        assertThat(rolesDoUsuario()).doesNotContain("PROVIDER");
    }

    @Test
    void deveRecusarAprovacaoSemAutenticacao() throws Exception {
        Prestador prestador = criarPrestador(criarUsuarioKeycloak(), StatusPrestador.PENDENTE);

        mockMvc.perform(requisicaoAprovacao(prestador.getId())).andExpect(status().isUnauthorized());

        assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPrestador.PENDENTE);
        assertThat(rolesDoUsuario()).doesNotContain("PROVIDER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RASCUNHO", "ATIVO", "REJEITADO"})
    void deveRecusarTransicaoInvalidaSemAtribuirRole(StatusPrestador statusInicial) throws Exception {
        Prestador prestador = criarPrestador(criarUsuarioKeycloak(), statusInicial);

        mockMvc.perform(comoAdministrador(requisicaoAprovacao(prestador.getId()), UUID.randomUUID()))
                .andExpect(status().isConflict());

        assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getStatus()).isEqualTo(statusInicial);
        assertThat(rolesDoUsuario()).doesNotContain("PROVIDER");
    }

    @Test
    void deveReativarPrestadorSuspensoEAtribuirProvider() throws Exception {
        Prestador prestador = criarPrestador(criarUsuarioKeycloak(), StatusPrestador.SUSPENSO);

        mockMvc.perform(comoAdministrador(requisicaoAprovacao(prestador.getId()), UUID.randomUUID()))
                .andExpect(status().isOk());

        Prestador salvo = prestadorRepository.findById(prestador.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusPrestador.ATIVO);
        assertThat(salvo.isAtivo()).isTrue();
        assertThat(rolesDoUsuario()).contains("CUSTOMER", "PROVIDER");
    }

    @Test
    void deveRecusarAutoaprovacaoSemAtribuirProvider() throws Exception {
        UUID usuarioId = criarUsuarioKeycloak();
        Prestador prestador = criarPrestador(usuarioId, StatusPrestador.PENDENTE);

        mockMvc.perform(comoAdministrador(requisicaoAprovacao(prestador.getId()), usuarioId))
                .andExpect(status().isForbidden());

        assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPrestador.PENDENTE);
        assertThat(rolesDoUsuario()).doesNotContain("PROVIDER");
    }

    @Test
    void deveRetornar404QuandoSolicitacaoNaoExistir() throws Exception {
        mockMvc.perform(comoAdministrador(requisicaoAprovacao(Long.MAX_VALUE),
                        UUID.randomUUID())).andExpect(status().isNotFound());
    }

    private Prestador criarPrestador(UUID usuarioId, StatusPrestador statusInicial) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(usuarioId));
        prestadorCriado = prestadorRepository.saveAndFlush(Prestador.builder()
                .perfilUsuario(perfil).nomeEstab("Studio aprovação")
                .status(statusInicial).ativo(false).build());
        return prestadorCriado;
    }

    private UUID criarUsuarioKeycloak() {
        var resposta = clienteAdministrativo().post().uri("/admin/realms/wonder/users")
                .body(Map.of("username", "cliente-" + UUID.randomUUID(), "enabled", true))
                .retrieve().toBodilessEntity();
        String localizacao = resposta.getHeaders().getLocation().toString();
        usuarioCriado = UUID.fromString(localizacao.substring(localizacao.lastIndexOf('/') + 1));
        return usuarioCriado;
    }

    private String[] rolesDoUsuario() {
        KeycloakRoleResponse[] roles = clienteAdministrativo().get()
                .uri("/admin/realms/wonder/users/{id}/role-mappings/realm/composite", usuarioCriado)
                .retrieve().body(KeycloakRoleResponse[].class);
        return Arrays.stream(roles).map(KeycloakRoleResponse::name).toArray(String[]::new);
    }

    private RestClient clienteAdministrativo() {
        String token = keycloakClient.obterToken();
        return RestClient.builder().baseUrl(enderecoKeycloak())
                .defaultHeaders(cabecalhos -> cabecalhos.setBearerAuth(token)).build();
    }

    private MockHttpServletRequestBuilder comoAdministrador(MockHttpServletRequestBuilder requisicao, UUID usuarioId) {
        return requisicao.with(jwt().jwt(token -> token.subject(usuarioId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private MockHttpServletRequestBuilder requisicaoAprovacao(Long prestadorId) {
        return patch("/admin/prestadores/{id}/status", prestadorId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ativo\"}");
    }

    private static String enderecoKeycloak() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }
}
