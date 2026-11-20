package br.edu.ifrn.wonder.core.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import br.edu.ifrn.wonder.core.config.KeycloakProperties;
import br.edu.ifrn.wonder.core.service.UsuarioAdminService;

class KeycloakListagemTest {
    MockRestServiceServer servidor;
    KeycloakClient cliente;
    UsuarioAdminService servico;
    final String primeiroId = "00000000-0000-0000-0000-000000000001";
    final String segundoId = "00000000-0000-0000-0000-000000000002";

    @BeforeEach
    void preparar() {
        var construtor = RestClient.builder().baseUrl("http://keycloak");
        servidor = MockRestServiceServer.bindTo(construtor).build();
        cliente = new KeycloakClient(construtor.build(), new KeycloakProperties("http://keycloak", "wonder", "core",
                "teste", Duration.ofSeconds(3), Duration.ofSeconds(5)));
        servico = new UsuarioAdminService(cliente);
    }

    @Test
    void deveExcluirContasTecnicasDeduplicarEOrdenarAntesDeContarEPaginar() {
        token();
        responder("/users?search=&first=0&max=100&briefRepresentation=false", """
                [{"id":"%s","username":"ana","enabled":true},
                 {"id":"%s","username":"ana","enabled":false},
                 {"id":"%s","username":"ana","enabled":true},
                 {"id":"%s","username":"tecnica","serviceAccountClientId":"core"}]
                """.formatted(segundoId, primeiroId, segundoId, UUID.randomUUID()));
        var pagina = servico.listar("", null, 1, 1);
        assertThat(pagina.total()).isEqualTo(2);
        assertThat(pagina.usuarios()).singleElement().satisfies(usuario -> assertThat(usuario.id().toString()).isEqualTo(segundoId));
        assertThat(pagina.temProxima()).isFalse();
        servidor.verify();
    }

    @Test
    void deveCruzarBuscaComPapelCompostoSemContarContaDuasVezes() {
        token();
        responder("/users?search=Ana%2B%20%26%20%C3%A1&first=0&max=100&briefRepresentation=false", """
                [{"id":"%s","username":"ana","enabled":false},{"id":"%s","username":"outra"}]
                """.formatted(primeiroId, segundoId));
        responder("/roles?first=0&max=100", """
                [{"name":"CUSTOMER","composite":false},{"name":"padrao","composite":true},
                 {"name":"grupo","composite":true}]
                """);
        responder("/roles/padrao/composites/realm", "[{\"id\":\"customer\",\"name\":\"CUSTOMER\"}]");
        responder("/roles/grupo/composites/realm", "[{\"id\":\"padrao\",\"name\":\"padrao\"}]");
        for (String papel : new String[]{"CUSTOMER", "padrao", "grupo"}) {
            responder("/roles/" + papel + "/users?first=0&max=100&briefRepresentation=false",
                    "[{\"id\":\"" + primeiroId + "\"}]");
        }
        var pagina = servico.listar(" Ana+ & á ", "CUSTOMER", 0, 20);
        assertThat(pagina.total()).isEqualTo(1);
        assertThat(pagina.usuarios()).singleElement().satisfies(usuario -> assertThat(usuario.ativo()).isFalse());
        servidor.verify();
    }

    @Test
    void deveRetornar503SeFalharUmaPaginaPosteriorDaFonte() {
        token();
        String usuarios = IntStream.range(0, 100).mapToObj(indice ->
                "{\"id\":\"" + UUID.randomUUID() + "\",\"username\":\"usuario" + indice + "\"}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        responder("/users?search=&first=0&max=100&briefRepresentation=false", usuarios);
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder/users?search=&first=100&max=100&briefRepresentation=false"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> servico.listar("", null, 0, 20))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        excecao -> assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        servidor.verify();
    }

    private void token() {
        servidor.expect(requestTo("http://keycloak/realms/wonder/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
    }

    private void responder(String rota, String resposta) {
        servidor.expect(requestTo("http://keycloak/admin/realms/wonder" + rota))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));
    }
}
