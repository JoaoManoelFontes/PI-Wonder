package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoFrontendContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PrestadorRepository prestadorRepository;

    @Test
    void deveManterFixturesDaSimulacaoCompativeisComOFormatoHttpAtual() throws Exception {
        Map<?, ?> fixture;
        try (var entrada = new ClassPathResource("catalogo-frontend.json").getInputStream()) {
            fixture = jsonMapper.readValue(entrada, Map.class);
        }
        Map<?, ?> respostas = (Map<?, ?>) fixture.get("respostas");
        Long prestadorId = prestadorRepository.findByPerfilUsuarioId(
                UUID.fromString("10000000-0000-0000-0000-000000000001")).orElseThrow().getId();

        for (String rota : List.of("/catalogo/categorias", "/catalogo/prestadores",
                "/catalogo/prestadores/1", "/catalogo/prestadores/1/horarios", "/catalogo/prestadores/1/categorias")) {
            String rotaReal = rota.replace("/prestadores/1", "/prestadores/" + prestadorId);
            String corpo = mockMvc.perform(get(rotaReal)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            validarFormato(respostas.get(rota), jsonMapper.readValue(corpo, Object.class), rota);
        }
    }

    // IDs, quantidade de registros e valores ilustrativos não são fixados como contrato.
    private void validarFormato(Object exemplo, Object resposta, String caminho) {
        if (exemplo instanceof Map<?, ?> campos) {
            assertThat(resposta).as(caminho).isInstanceOf(Map.class);
            Map<?, ?> objeto = (Map<?, ?>) resposta;
            assertThat(objeto.keySet()).as(caminho).isEqualTo(campos.keySet());
            for (var campo : campos.entrySet()) {
                validarFormato(campo.getValue(), objeto.get(campo.getKey()), caminho + "." + campo.getKey());
            }
        } else if (exemplo instanceof List<?> itens) {
            assertThat(resposta).as(caminho).isInstanceOf(List.class);
            List<?> registros = (List<?>) resposta;
            assertThat(registros).as("Seed deve fornecer exemplo para " + caminho).isNotEmpty();
            for (Object registro : registros) {
                validarFormato(itens.getFirst(), registro, caminho + "[]");
            }
        } else if (exemplo instanceof Number) {
            assertThat(resposta).as(caminho).isInstanceOf(Number.class);
        } else if (exemplo != null) {
            assertThat(resposta).as(caminho).isInstanceOf(exemplo.getClass());
        } else {
            assertThat(resposta == null || resposta instanceof String).as(caminho + " aceita null ou texto").isTrue();
        }
    }
}
