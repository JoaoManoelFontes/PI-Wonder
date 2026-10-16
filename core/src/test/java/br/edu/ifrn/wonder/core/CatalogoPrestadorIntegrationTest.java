package br.edu.ifrn.wonder.core;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoPrestadorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveRetornarDetalhePublicoDoPrestadorSemAutenticacao() throws Exception {
        Long prestadorId = jdbcTemplate.queryForObject(
                "SELECT id FROM prestadores WHERE nome_estab = 'Studio Wonder'",
                Long.class
        );

        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}", prestadorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(prestadorId))
                .andExpect(jsonPath("$.nomeEstab").value("Studio Wonder"))
                .andExpect(jsonPath("$.status").value("ativo"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void deveRetornarNotFoundQuandoPrestadorNaoExiste() throws Exception {
        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }
}
