package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostgresTestcontainersIntegrationTest {

    @Autowired
    @Qualifier("postgresContainer")
    private PostgreSQLContainer postgresContainer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    void deveSubirPostgresEExecutarConsulta() {
        assertThat(postgresContainer.isRunning()).isTrue();

        Integer resultado = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

        assertThat(resultado).isEqualTo(1);
    }

    @Test
    void administradorDeveCadastrarCategoriaNoPostgres() throws Exception {
        mockMvc.perform(post("/admin/categorias")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Podologia",
                                  "descricao": "Cuidados especializados para os pés"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Podologia"))
                .andExpect(jsonPath("$.descricao").value("Cuidados especializados para os pés"))
                .andExpect(jsonPath("$.status").value("ativa"));

        assertThat(categoriaRepository.findAll())
                .filteredOn(categoria -> categoria.getNome().equals("Podologia"))
                .singleElement()
                .satisfies(categoria -> {
                    assertThat(categoria.getDescricao()).isEqualTo("Cuidados especializados para os pés");
                    assertThat(categoria.getStatus()).isEqualTo(StatusCategoria.ATIVA);
                    assertThat(categoria.getCriadoEm()).isNotNull();
                });
    }
}
