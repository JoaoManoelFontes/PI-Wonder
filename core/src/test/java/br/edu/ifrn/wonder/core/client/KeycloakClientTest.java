package br.edu.ifrn.wonder.core.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import br.edu.ifrn.wonder.core.config.KeycloakProperties;
import br.edu.ifrn.wonder.core.dto.KeycloakRoleResponse;

class KeycloakClientTest {

    private MockRestServiceServer servidor;
    private KeycloakClient cliente;

    @BeforeEach
    void configurar() {
        RestClient.Builder construtor = RestClient.builder().baseUrl("http://keycloak");
        servidor = MockRestServiceServer.bindTo(construtor).build();
        cliente = new KeycloakClient(construtor.build(), propriedades("secret+local&"));
    }

    @Test
    void deveCodificarBuscaSemTransformarMaisEmEspacoOuCriarOutroParametro() {
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/users?search=maria%2Bteste%40example.com%20%26&first=0&max=21&briefRepresentation=false"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(cliente.listarUsuarios("token", "maria+teste@example.com &", 0, 21)).isEmpty();
        servidor.verify();
    }

    @Test
    void deveEnviarCredenciaisComoFormularioEAdicionarApenasARoleSolicitada() {
        UUID usuarioId = UUID.randomUUID();
        servidor.expect(requestTo("http://keycloak/realms/wonder/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andExpect(content().string(containsString("client_id=wonder-core")))
                .andExpect(content().string(containsString("client_secret=secret%2Blocal%26")))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/roles/PROVIDER"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"id\":\"id-provider\",\"name\":\"PROVIDER\",\"composite\":false}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/users/" + usuarioId + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[{\"id\":\"id-provider\",\"name\":\"PROVIDER\"}]"))
                .andRespond(withNoContent());

        String token = cliente.obterToken();
        KeycloakRoleResponse role = cliente.buscarRole(token, "PROVIDER");
        cliente.adicionarRole(usuarioId, token, role);

        assertThat(token).isEqualTo("token");
        assertThat(role).isEqualTo(new KeycloakRoleResponse("id-provider", "PROVIDER"));
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"access_token\":\"\"}"})
    void deveRejeitarRespostaSemToken(String resposta) {
        servidor.expect(requestTo("http://keycloak/realms/wonder/protocol/openid-connect/token"))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        assertThatThrownBy(cliente::obterToken).isInstanceOf(IllegalStateException.class);
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"id\":\"id-admin\",\"name\":\"ADMIN\"}"})
    void deveRejeitarRespostaSemIdentificacaoDaRoleSolicitada(String resposta) {
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/roles/PROVIDER"))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.buscarRole("token", "PROVIDER"))
                .isInstanceOf(IllegalStateException.class);
        servidor.verify();
    }

    @Test
    void deveRejeitarSecretAusenteAntesDeEnviarRequisicao() {
        cliente = new KeycloakClient(RestClient.create("http://keycloak"), propriedades(""));

        assertThatThrownBy(cliente::obterToken).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveBuscarTodasAsPaginasDeAdministradoresComAutenticacao() {
        String primeiraPagina = IntStream.range(0, 100)
                .mapToObj(indice -> "{\"email\":\"admin" + indice + "@example.com\",\"enabled\":true}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/roles/ADMIN/users?first=0&max=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess(primeiraPagina, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/roles/ADMIN/users?first=100&max=100"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("[{\"email\":\"ultimo@example.com\"}]", MediaType.APPLICATION_JSON));

        var usuarios = cliente.listarUsuariosPorRole("token", "ADMIN");

        assertThat(usuarios).hasSize(101);
        assertThat(usuarios.getLast().email()).isEqualTo("ultimo@example.com");
        servidor.verify();
    }

    @Test
    void deveAceitarListaVaziaDeAdministradores() {
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/roles/ADMIN/users?first=0&max=100"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(cliente.listarUsuariosPorRole("token", "ADMIN")).isEmpty();
        servidor.verify();
    }

    @Test
    void deveRejeitarRespostaSemListaDeUsuarios() {
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/roles/ADMIN/users?first=0&max=100"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.listarUsuariosPorRole("token", "ADMIN"))
                .isInstanceOf(IllegalStateException.class);
        servidor.verify();
    }

    @Test
    void deveAutenticarEBuscarEmailPeloKeycloakId() {
        UUID keycloakId = UUID.randomUUID();
        esperarToken();
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/users/" + keycloakId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"email\":\" ana@example.com \",\"username\":\"ana\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(cliente.buscarEmailPorKeycloakId(keycloakId)).isEqualTo("ana@example.com");
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"email\":null}", "{\"email\":\" \"}"})
    void deveRejeitarRespostaSemEmail(String resposta) {
        UUID keycloakId = UUID.randomUUID();
        esperarToken();
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/users/" + keycloakId))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.buscarEmailPorKeycloakId(keycloakId))
                .isInstanceOf(IllegalStateException.class);
        servidor.verify();
    }

    @Test
    void devePropagarUsuarioNaoEncontradoNoKeycloak() {
        UUID keycloakId = UUID.randomUUID();
        esperarToken();
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/users/" + keycloakId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> cliente.buscarEmailPorKeycloakId(keycloakId))
                .isInstanceOf(RestClientResponseException.class);
        servidor.verify();
    }

    private void esperarToken() {
        servidor.expect(requestTo("http://keycloak/realms/wonder/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
    }

    private KeycloakProperties propriedades(String secret) {
        return new KeycloakProperties("http://keycloak", "wonder", "wonder-core", secret,
                Duration.ofSeconds(3), Duration.ofSeconds(5));
    }
}
