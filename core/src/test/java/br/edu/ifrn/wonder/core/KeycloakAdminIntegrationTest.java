package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.config.KeycloakConfig;
import br.edu.ifrn.wonder.core.config.KeycloakProperties;
import br.edu.ifrn.wonder.core.dto.KeycloakRoleResponse;
import br.edu.ifrn.wonder.core.service.KeycloakAdminService;

@Testcontainers
@SpringJUnitConfig(classes = {KeycloakConfig.class, KeycloakClient.class, KeycloakAdminService.class})
class KeycloakAdminIntegrationTest {

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
    private KeycloakClient keycloakClient;

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("wonder.keycloak.server-url", KeycloakAdminIntegrationTest::enderecoKeycloak);
        registro.add("wonder.keycloak.realm", () -> "wonder");
        registro.add("wonder.keycloak.client-id", () -> "wonder-core");
        registro.add("wonder.keycloak.client-secret", () -> SECRET);
        registro.add("wonder.keycloak.connect-timeout", () -> "3s");
        registro.add("wonder.keycloak.read-timeout", () -> "5s");
    }

    @Test
    void deveVerificarBuscaEMembrosPaginadosComRepresentacaoCompleta() {
        String token = keycloakClient.obterToken();
        RestClient cliente = RestClient.create(enderecoKeycloak());
        List<UUID> criados = new ArrayList<>();
        String prefixo = "listagem-" + UUID.randomUUID();
        try {
            for (int indice = 0; indice < 125; indice++) {
                var resposta = cliente.post().uri("/admin/realms/wonder/users")
                        .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                        .body(Map.of("username", prefixo + String.format("-%03d", indice),
                                "firstName", "Ana", "email", prefixo + indice + "@example.com",
                                "enabled", indice != 124)).retrieve().toBodilessEntity();
                String localizacao = resposta.getHeaders().getLocation().toString();
                UUID id = UUID.fromString(localizacao.substring(localizacao.lastIndexOf('/') + 1));
                criados.add(id);
                keycloakClient.adicionarRole(id, token, keycloakClient.buscarRole(token, "PROVIDER"));
            }
            assertThat(keycloakClient.listarUsuarios(token, prefixo, 0, 100)).hasSize(100);
            var ultima = keycloakClient.listarUsuarios(token, prefixo, 100, 100);
            assertThat(ultima).hasSize(25);
            assertThat(ultima).anySatisfy(usuario -> assertThat(usuario.get("enabled")).isEqualTo(false));
            assertThat(keycloakClient.listarMembrosPapel(token, "PROVIDER", 0, 100)).hasSize(100);
            assertThat(keycloakClient.listarMembrosPapel(token, "PROVIDER", 100, 100))
                    .hasSize(25).allSatisfy(usuario -> assertThat(usuario).containsKeys("id", "username", "firstName", "enabled"));
            // Keycloak 26 já omite service accounts na consulta /users padrão.
            assertThat(keycloakClient.listarUsuarios(token, "service-account-wonder-core", 0, 100)).isEmpty();
            var listagem = new br.edu.ifrn.wonder.core.service.UsuarioAdminService(keycloakClient);
            assertThat(listagem.listar(prefixo, "CUSTOMER", 100, 20).total()).isEqualTo(125);
            var pagina = listagem.listar(prefixo, "PROVIDER", 100, 20);
            assertThat(pagina.total()).isEqualTo(125);
            assertThat(pagina.usuarios()).hasSize(20);
            assertThat(pagina.temProxima()).isTrue();
            assertThat(listagem.listar(prefixo, null, 123, 10).usuarios()).hasSize(2);
            assertThat(listagem.listar(prefixo, null, 140, 10).total()).isEqualTo(125);
            assertThat(listagem.listar(prefixo, "ADMIN", 0, 20).total()).isZero();
        } finally {
            for (UUID id : criados) {
                cliente.delete().uri("/admin/realms/wonder/users/{id}", id)
                        .headers(cabecalhos -> cabecalhos.setBearerAuth(token)).retrieve().toBodilessEntity();
            }
        }
    }

    @Test
    void deveContarUsuariosHumanosSemDuplicarPapeisNemIncluirContaDeServico() {
        String token = keycloakClient.obterToken();
        RestClient cliente = RestClient.create(enderecoKeycloak());
        List<UUID> usuariosCriados = new ArrayList<>();
        // O realm importado contém somente a service account wonder-core.
        assertThat(keycloakAdminService.contarUsuarios()).isZero();
        try {
            for (boolean habilitado : List.of(true, false)) {
                var criacao = cliente.post().uri("/admin/realms/wonder/users")
                        .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                        .body(Map.of("username", "indicadores-" + UUID.randomUUID(), "enabled", habilitado))
                        .retrieve().toBodilessEntity();
                String localizacao = criacao.getHeaders().getLocation().toString();
                UUID usuarioId = UUID.fromString(localizacao.substring(localizacao.lastIndexOf('/') + 1));
                usuariosCriados.add(usuarioId);
                if (habilitado) {
                    keycloakAdminService.adicionarRoleProvider(usuarioId);
                    assertThat(rolesEfetivas(cliente, token, usuarioId)).contains("CUSTOMER", "PROVIDER");
                }
            }

            // Este contexto não contém banco/perfis locais: são contas apenas no Keycloak.
            assertThat(keycloakAdminService.contarUsuarios()).isEqualTo(2);
        } finally {
            for (UUID usuarioId : usuariosCriados) {
                cliente.delete().uri("/admin/realms/wonder/users/{id}", usuarioId)
                        .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                        .retrieve().toBodilessEntity();
            }
        }
    }

    @Test
    void deveConsultarEmailsApenasDosUsuariosComRoleAdmin() {
        String token = keycloakClient.obterToken();
        RestClient cliente = RestClient.create(enderecoKeycloak());
        List<UUID> usuariosCriados = new ArrayList<>();
        String emailAdmin = "admin-" + UUID.randomUUID() + "@example.com";
        String emailCliente = "cliente-" + UUID.randomUUID() + "@example.com";
        try {
            for (String email : List.of(emailAdmin, emailCliente)) {
                var criacao = cliente.post().uri("/admin/realms/wonder/users")
                        .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                        .body(Map.of("username", email, "email", email, "enabled", true))
                        .retrieve().toBodilessEntity();
                String localizacao = criacao.getHeaders().getLocation().toString();
                UUID usuarioId = UUID.fromString(localizacao.substring(localizacao.lastIndexOf('/') + 1));
                usuariosCriados.add(usuarioId);
                if (email.equals(emailAdmin)) {
                    keycloakClient.adicionarRole(usuarioId, token, keycloakClient.buscarRole(token, "ADMIN"));
                }
            }

            assertThat(keycloakAdminService.listarEmailsAdministradores())
                    .contains(emailAdmin).doesNotContain(emailCliente);
        } finally {
            for (UUID usuarioId : usuariosCriados) {
                cliente.delete().uri("/admin/realms/wonder/users/{id}", usuarioId)
                        .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                        .retrieve().toBodilessEntity();
            }
        }
    }

    @Test
    void deveAdicionarProviderPreservarCustomerEAceitarRepeticao() {
        String token = keycloakClient.obterToken();
        RestClient cliente = RestClient.create(enderecoKeycloak());
        var criacao = cliente.post().uri("/admin/realms/wonder/users")
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                .body(Map.of("username", "cliente-" + UUID.randomUUID(), "enabled", true))
                .retrieve().toBodilessEntity();
        assertThat(criacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String localizacao = criacao.getHeaders().getLocation().toString();
        UUID usuarioId = UUID.fromString(localizacao.substring(localizacao.lastIndexOf('/') + 1));

        try {
            assertThat(rolesEfetivas(cliente, token, usuarioId)).contains("CUSTOMER").doesNotContain("PROVIDER");

            keycloakAdminService.adicionarRoleProvider(usuarioId);
            keycloakAdminService.adicionarRoleProvider(usuarioId);

            assertThat(rolesEfetivas(cliente, token, usuarioId)).contains("CUSTOMER", "PROVIDER").doesNotContain("ADMIN");
            KeycloakRoleResponse[] rolesDiretas = cliente.get()
                    .uri("/admin/realms/wonder/users/{id}/role-mappings/realm", usuarioId)
                    .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                    .retrieve().body(KeycloakRoleResponse[].class);
            assertThat(rolesDiretas).filteredOn(role -> "PROVIDER".equals(role.name())).hasSize(1);
        } finally {
            cliente.delete().uri("/admin/realms/wonder/users/{id}", usuarioId)
                    .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                    .retrieve().toBodilessEntity();
        }
    }

    @Test
    void deveInformarFalhaQuandoUsuarioNaoExistir() {
        assertThatThrownBy(() -> keycloakAdminService.adicionarRoleProvider(UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void deveRecusarCredencialInvalidaSemAdicionarRole() {
        KeycloakProperties propriedades = new KeycloakProperties(enderecoKeycloak(), "wonder", "wonder-core",
                UUID.randomUUID().toString(), Duration.ofSeconds(3), Duration.ofSeconds(5));
        KeycloakAdminService servico = new KeycloakAdminService(
                new KeycloakClient(RestClient.create(enderecoKeycloak()), propriedades));

        assertThatThrownBy(() -> servico.adicionarRoleProvider(UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao -> {
                    assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(excecao.getCause()).isInstanceOf(HttpClientErrorException.Unauthorized.class);
                });
    }

    private String[] rolesEfetivas(RestClient cliente, String token, UUID usuarioId) {
        KeycloakRoleResponse[] roles = cliente.get()
                .uri("/admin/realms/wonder/users/{id}/role-mappings/realm/composite", usuarioId)
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                .retrieve().body(KeycloakRoleResponse[].class);
        return Arrays.stream(roles).map(KeycloakRoleResponse::name).toArray(String[]::new);
    }

    private static String enderecoKeycloak() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }
}
