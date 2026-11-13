package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminUsuarioIntegrationTest.BancoTeste.class)
class AdminUsuarioIntegrationTest {
    private static final String SECRET = UUID.randomUUID() + "+&/";
    private static final String SENHA = UUID.randomUUID().toString();
    private static final String CLIENTE_TESTE = "validacao-usuarios";

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
            .withEnv("KEYCLOAK_CLIENT_SECRET", SECRET)
            .withEnv("JAVA_OPTS_KC_HEAP", "-Xms128m -Xmx512m")
            .withCopyToContainer(Transferable.of(realmTeste()), "/opt/keycloak/data/import/wonder-realm.json")
            .withCommand("start-dev", "--import-realm").withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/wonder/.well-known/openid-configuration").forPort(8080))
            .withStartupTimeout(Duration.ofMinutes(2));

    @Autowired MockMvc mvc;
    @Autowired KeycloakClient keycloak;
    @Autowired PerfilUsuarioRepository perfis;

    private final List<UUID> usuariosCriados = new ArrayList<>();
    private String tokenServico;
    private UUID administradorId;
    private String tokenAdmin;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("wonder.keycloak.server-url", AdminUsuarioIntegrationTest::endereco);
        registro.add("wonder.keycloak.client-secret", () -> SECRET);
    }

    @BeforeEach
    void prepararAdmin() {
        tokenServico = keycloak.obterToken();
        administradorId = criarUsuario("ADMIN");
        tokenAdmin = login(administradorId).get("access_token").toString();
    }

    @AfterEach
    void limpar() {
        for (UUID id : usuariosCriados) {
            http().delete().uri("/admin/realms/wonder/users/{id}", id)
                    .headers(h -> h.setBearerAuth(tokenServico)).retrieve().toBodilessEntity();
            perfis.findByKeycloakId(id).ifPresent(perfis::delete);
        }
    }

    @Test
    void deveEditarIdentidadePreservandoUsernameRolesEPerfil() throws Exception {
        UUID id = criarUsuario("CUSTOMER");
        String username = keycloak.buscarUsuario(tokenServico, id).get("username").toString();
        String token = login(id).get("access_token").toString();
        mvc.perform(get("/perfis/eu").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        var perfil = perfis.findByKeycloakId(id).orElseThrow();
        perfil.setNumeroTelefone("84999998888");
        perfil.setFotoUrl("fotos/perfil-preservado.jpg");
        perfis.saveAndFlush(perfil);
        String email = "novo-" + id + "@example.com";

        mvc.perform(put("/admin/usuarios/{id}", id).header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  Maria  \",\"sobrenome\":\" Silva \",\"email\":\" " + email + " \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Maria"))
                .andExpect(jsonPath("$.sobrenome").value("Silva"))
                .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.username").value(username));
        Map<String, Object> usuario = keycloak.buscarUsuario(tokenServico, id);
        assertThat(usuario.get("emailVerified")).isEqualTo(false);
        assertThat(usuario.get("enabled")).isEqualTo(true);
        var preservado = perfis.findByKeycloakId(id).orElseThrow();
        assertThat(preservado.getId()).isEqualTo(perfil.getId());
        assertThat(preservado.getNumeroTelefone()).isEqualTo("84999998888");
        assertThat(preservado.getFotoUrl()).isEqualTo("fotos/perfil-preservado.jpg");
        // O token e a role anteriores continuam válidos após editar somente a identidade.
        mvc.perform(get("/admin/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/perfis/eu").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void deveBloquearTokenAtualRefreshENovoLoginEAceitarReativacaoComNovaSessao() throws Exception {
        UUID id = criarUsuario("CUSTOMER");
        Map<?, ?> sessao = login(id);
        String token = sessao.get("access_token").toString();
        mvc.perform(get("/perfis/eu").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        var perfilId = perfis.findByKeycloakId(id).orElseThrow().getId();
        alterarStatus(id, false);
        alterarStatus(id, false); // Idempotência e nova tentativa de encerrar sessões.
        mvc.perform(get("/perfis/eu").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        assertThat(perfis.findByKeycloakId(id).orElseThrow().getId()).isEqualTo(perfilId);
        var refresh = new LinkedMultiValueMap<String, String>();
        refresh.add("grant_type", "refresh_token");
        refresh.add("client_id", CLIENTE_TESTE);
        refresh.add("refresh_token", sessao.get("refresh_token").toString());
        assertThatThrownBy(() -> enviarFormulario(refresh)).isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> login(id)).isInstanceOfSatisfying(HttpClientErrorException.class,
                e -> assertThat(e.getStatusCode().is4xxClientError()).isTrue());
        alterarStatus(id, true);
        mvc.perform(get("/perfis/eu").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        String novo = login(id).get("access_token").toString();
        mvc.perform(get("/perfis/eu").header("Authorization", "Bearer " + novo)).andExpect(status().isOk());
    }

    @Test
    void deveConsultarBuscarEPaginarSemExporContasDeServico() throws Exception {
        UUID id = criarUsuario("CUSTOMER");
        mvc.perform(get("/admin/usuarios").param("busca", keycloak.buscarUsuario(tokenServico, id).get("username").toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarios.length()").value(1))
                .andExpect(jsonPath("$.usuarios[0].id").value(id.toString()))
                .andExpect(jsonPath("$.temProxima").value(false));
        mvc.perform(get("/admin/usuarios").param("limite", "1").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarios.length()").value(1))
                .andExpect(jsonPath("$.temProxima").value(true));
        mvc.perform(get("/admin/usuarios").param("busca", "service-account")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarios.length()").value(0));
        mvc.perform(get("/admin/usuarios/{id}", id).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        mvc.perform(get("/admin/usuarios/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRecusarDuplicidadeDadosInvalidosEAutoDesativacao() throws Exception {
        UUID id = criarUsuario("CUSTOMER");
        mvc.perform(put("/admin/usuarios/{id}", id).header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Novo\",\"email\":\""
                                + keycloak.buscarUsuario(tokenServico, administradorId).get("email") + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/admin/usuarios/{id}", id).header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\" \",\"email\":\"invalido\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/admin/usuarios/{id}/status", id).header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/usuarios").param("inicio", "-1").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/admin/usuarios/{id}/status", administradorId).header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isConflict());
        assertThat(keycloak.buscarUsuario(tokenServico, administradorId).get("enabled")).isEqualTo(true);
    }

    @Test
    void deveRestringirTodasAsOperacoesAoAdmin() throws Exception {
        for (String role : List.of("CUSTOMER", "PROVIDER")) {
            UUID id = criarUsuario(role);
            String token = login(id).get("access_token").toString();
            for (String acesso : List.of("", "Bearer invalido", "Bearer " + token)) {
                int esperado = acesso.endsWith(token) ? 403 : 401;
                mvc.perform(get("/admin/usuarios").header("Authorization", acesso)).andExpect(status().is(esperado));
                mvc.perform(get("/admin/usuarios/{id}", id).header("Authorization", acesso)).andExpect(status().is(esperado));
                mvc.perform(put("/admin/usuarios/{id}", id).header("Authorization", acesso)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste\",\"email\":\"teste@example.com\"}"))
                        .andExpect(status().is(esperado));
                mvc.perform(patch("/admin/usuarios/{id}/status", id).header("Authorization", acesso)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                        .andExpect(status().is(esperado));
            }
        }
    }

    private void alterarStatus(UUID id, boolean ativo) throws Exception {
        mvc.perform(patch("/admin/usuarios/{id}/status", id).header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":" + ativo + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(ativo));
    }

    private UUID criarUsuario(String role) {
        UUID nome = UUID.randomUUID();
        var criacao = http().post().uri("/admin/realms/wonder/users").headers(h -> h.setBearerAuth(tokenServico))
                .body(Map.of("username", nome.toString(), "email", nome + "@example.com", "firstName", "Teste",
                        "lastName", "Wonder", "emailVerified", true, "enabled", true,
                        "credentials", List.of(Map.of("type", "password", "value", SENHA, "temporary", false))))
                .retrieve().toBodilessEntity();
        String caminho = criacao.getHeaders().getLocation().getPath();
        UUID id = UUID.fromString(caminho.substring(caminho.lastIndexOf('/') + 1));
        usuariosCriados.add(id);
        keycloak.adicionarRole(id, tokenServico, keycloak.buscarRole(tokenServico, role));
        return id;
    }

    private Map<?, ?> login(UUID id) {
        var formulario = new LinkedMultiValueMap<String, String>();
        formulario.add("grant_type", "password");
        formulario.add("client_id", CLIENTE_TESTE);
        formulario.add("username", keycloak.buscarUsuario(tokenServico, id).get("username").toString());
        formulario.add("password", SENHA);
        return enviarFormulario(formulario);
    }

    private Map<?, ?> enviarFormulario(LinkedMultiValueMap<String, String> formulario) {
        return http().post().uri("/realms/wonder/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario).retrieve().body(Map.class);
    }

    private RestClient http() { return RestClient.create(endereco()); }
    private static String endereco() { return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080); }

    @TestConfiguration(proxyBeanMethods = false)
    static class BancoTeste {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer("postgres:15");
        }
    }

    private static String realmTeste() {
        try (var arquivo = AdminUsuarioIntegrationTest.class.getResourceAsStream("/keycloak/wonder-realm.json")) {
            // Grant de senha existe somente neste container descartável para emitir sessões reais nos testes.
            return new String(arquivo.readAllBytes(), StandardCharsets.UTF_8).replace("\"clients\": [", """
                    "clients": [{"clientId":"validacao-usuarios","enabled":true,"protocol":"openid-connect",
                    "publicClient":true,"standardFlowEnabled":false,"directAccessGrantsEnabled":true,
                    "fullScopeAllowed":true,"defaultClientScopes":["profile","email","roles"]},
                    """);
        } catch (IOException excecao) {
            throw new IllegalStateException(excecao);
        }
    }
}
