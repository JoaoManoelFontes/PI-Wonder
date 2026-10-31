package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrosPublicosHttpIntegrationTest {

    @LocalServerPort
    private int porta;

    @ParameterizedTest
    @CsvSource({
            "/catalogo/prestadores/2147483647, 404",
            "/catalogo/prestadores/2147483647/servicos, 404",
            "/catalogo/prestadores/invalido, 400",
            "/agendamentos/disponibilidade?prestador_id=1&servico_id=1&data=invalida, 400",
            "/agendamentos/dias-disponiveis?prestador_id=1&servico_id=1&mes=invalido, 400",
            "/agendamentos, 401",
            "/perfis/eu, 401",
            "/admin/categorias, 401",
            "/error, 401"
    })
    void devePreservarStatusHttpSemLiberarRotasProtegidas(String caminho, int statusEsperado) throws Exception {
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + porta + caminho))
                    .GET()
                    .build();

            HttpResponse<String> resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());

            assertThat(resposta.statusCode()).as(caminho).isEqualTo(statusEsperado);
        }
    }
}
