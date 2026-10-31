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

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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

    private KeycloakProperties propriedades(String secret) {
        return new KeycloakProperties("http://keycloak", "wonder", "wonder-core", secret,
                Duration.ofSeconds(3), Duration.ofSeconds(5));
    }
}
