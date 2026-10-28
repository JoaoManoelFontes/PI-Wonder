package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCategoriasIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void administradorDeveListarAtivasEInativasOrdenadasComContratoExistenteSemAlterarDados() throws Exception {
        removerCategoriasDeTeste();
        Categoria ultima = categoriaRepository.save(Categoria.builder().nome("Z Categoria ativa").build());
        Categoria primeira = categoriaRepository.saveAndFlush(Categoria.builder()
                .nome("A Categoria inativa").descricao("Descrição da categoria")
                .status(StatusCategoria.INATIVA).fotoUrl("https://imagens.exemplo/categoria.png").build());
        List<Map<String, Object>> categoriasAntes = jdbcTemplate.queryForList("SELECT * FROM categorias ORDER BY id");

        mockMvc.perform(get("/admin/categorias").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)))
                .andExpect(jsonPath("$[0].id").value(primeira.getId().intValue()))
                .andExpect(jsonPath("$[0].nome").value("A Categoria inativa"))
                .andExpect(jsonPath("$[0].descricao").value("Descrição da categoria"))
                .andExpect(jsonPath("$[0].status").value("inativa"))
                .andExpect(jsonPath("$[0].foto_url").value("https://imagens.exemplo/categoria.png"))
                .andExpect(jsonPath("$[1]", aMapWithSize(5)))
                .andExpect(jsonPath("$[1].id").value(ultima.getId().intValue()))
                .andExpect(jsonPath("$[1].nome").value("Z Categoria ativa"))
                .andExpect(jsonPath("$[1].status").value("ativa"))
                .andExpect(jsonPath("$[1].descricao").isEmpty())
                .andExpect(jsonPath("$[1].foto_url").isEmpty());

        assertThat(jdbcTemplate.queryForList("SELECT * FROM categorias ORDER BY id")).isEqualTo(categoriasAntes);
        mockMvc.perform(get("/catalogo/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ultima.getId().intValue()));
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistemCategorias() throws Exception {
        removerCategoriasDeTeste();
        mockMvc.perform(get("/admin/categorias").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void deveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/admin/categorias")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_PROVIDER", "SCOPE_openid"})
    void deveNegarAcessoSemRoleAdmin(String papel) throws Exception {
        mockMvc.perform(get("/admin/categorias").with(jwt().authorities(new SimpleGrantedAuthority(papel))))
                .andExpect(status().isForbidden());
    }

    @Test
    void devePermitirAdministradorComOutrosPapeis() throws Exception {
        mockMvc.perform(get("/admin/categorias").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("ROLE_PROVIDER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void administradorDeveCadastrarCategoriaAtivaComContratoExistente() throws Exception {
        removerCategoriasDeTeste();

        mockMvc.perform(post("/admin/categorias")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "  Maquiagem  ",
                                  "descricao": "  Serviços de maquiagem  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Maquiagem"))
                .andExpect(jsonPath("$.descricao").value("Serviços de maquiagem"))
                .andExpect(jsonPath("$.status").value("ativa"))
                .andExpect(jsonPath("$.foto_url").isEmpty());

        Categoria categoria = categoriaRepository.findAll().getFirst();
        assertThat(categoria.getNome()).isEqualTo("Maquiagem");
        assertThat(categoria.getDescricao()).isEqualTo("Serviços de maquiagem");
        assertThat(categoria.getStatus()).isEqualTo(StatusCategoria.ATIVA);
        assertThat(categoria.getFotoUrl()).isNull();
        assertThat(categoria.getCriadoEm()).isNotNull();
        assertThat(categoria.getAtualizadoEm()).isNotNull();
    }

    @Test
    void deveRecusarNomeDuplicadoSemDiferenciarMaiusculasEMinusculas() throws Exception {
        removerCategoriasDeTeste();
        Categoria existente = categoriaRepository.saveAndFlush(Categoria.builder()
                .nome("Maquiagem").descricao("Descrição original").status(StatusCategoria.INATIVA).build());

        mockMvc.perform(post("/admin/categorias")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"maquiagem","descricao":"Outra descrição"}
                                """))
                .andExpect(status().isConflict());

        assertThat(categoriaRepository.findAll()).singleElement().satisfies(categoria -> {
            assertThat(categoria.getId()).isEqualTo(existente.getId());
            assertThat(categoria.getDescricao()).isEqualTo("Descrição original");
            assertThat(categoria.getStatus()).isEqualTo(StatusCategoria.INATIVA);
        });
    }

    @Test
    void deveValidarNomeObrigatorioENoMaximoCemCaracteres() throws Exception {
        removerCategoriasDeTeste();
        String nomeLongo = "x".repeat(101);

        for (String corpo : List.of("{}", "{\"nome\":null}", "{\"nome\":\"   \"}",
                "{\"nome\":\"%s\"}".formatted(nomeLongo))) {
            mockMvc.perform(post("/admin/categorias")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isBadRequest());
        }

        assertThat(categoriaRepository.count()).isZero();
    }

    @Test
    void deveProtegerCadastroDeCategoria() throws Exception {
        String corpo = "{\"nome\":\"Maquiagem\"}";

        mockMvc.perform(post("/admin/categorias").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/admin/categorias")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROVIDER")))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDeveEditarNomeEDescricaoPreservandoEstadoEFoto() throws Exception {
        removerCategoriasDeTeste();
        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder()
                .nome("Maquiagem").descricao("Descrição antiga").status(StatusCategoria.INATIVA)
                .fotoUrl("https://imagens.exemplo/maquiagem.png").build());

        mockMvc.perform(put("/admin/categorias/{categoriaId}", categoria.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "  Maquiagem profissional  ",
                                  "descricao": "  Produção para eventos  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.id").value(categoria.getId().intValue()))
                .andExpect(jsonPath("$.nome").value("Maquiagem profissional"))
                .andExpect(jsonPath("$.descricao").value("Produção para eventos"))
                .andExpect(jsonPath("$.status").value("inativa"))
                .andExpect(jsonPath("$.foto_url").value("https://imagens.exemplo/maquiagem.png"));

        assertThat(categoriaRepository.findAll()).singleElement().satisfies(atualizada -> {
            assertThat(atualizada.getId()).isEqualTo(categoria.getId());
            assertThat(atualizada.getNome()).isEqualTo("Maquiagem profissional");
            assertThat(atualizada.getDescricao()).isEqualTo("Produção para eventos");
            assertThat(atualizada.getStatus()).isEqualTo(StatusCategoria.INATIVA);
            assertThat(atualizada.getFotoUrl()).isEqualTo("https://imagens.exemplo/maquiagem.png");
        });
    }

    @Test
    void devePermitirLimparDescricaoSemConflitarComOProprioNome() throws Exception {
        removerCategoriasDeTeste();
        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder()
                .nome("Maquiagem").descricao("Descrição antiga").build());

        mockMvc.perform(put("/admin/categorias/{categoriaId}", categoria.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"MAQUIAGEM\",\"descricao\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("MAQUIAGEM"))
                .andExpect(jsonPath("$.descricao").isEmpty());

        assertThat(categoriaRepository.findById(categoria.getId()).orElseThrow().getDescricao()).isNull();
    }

    @Test
    void deveRecusarNomeDeOutraCategoriaSemAlterarRegistros() throws Exception {
        removerCategoriasDeTeste();
        Categoria maquiagem = categoriaRepository.save(Categoria.builder()
                .nome("Maquiagem").descricao("Descrição original").build());
        Categoria cabelo = categoriaRepository.saveAndFlush(Categoria.builder().nome("Cabelo").build());

        mockMvc.perform(put("/admin/categorias/{categoriaId}", maquiagem.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"cabelo\",\"descricao\":\"Outra descrição\"}"))
                .andExpect(status().isConflict());

        assertThat(categoriaRepository.findById(maquiagem.getId()).orElseThrow().getNome()).isEqualTo("Maquiagem");
        assertThat(categoriaRepository.findById(maquiagem.getId()).orElseThrow().getDescricao())
                .isEqualTo("Descrição original");
        assertThat(categoriaRepository.findById(cabelo.getId()).orElseThrow().getNome()).isEqualTo("Cabelo");
        assertThat(categoriaRepository.count()).isEqualTo(2);
    }

    @Test
    void deveRetornarNaoEncontradoAoEditarCategoriaInexistente() throws Exception {
        removerCategoriasDeTeste();

        mockMvc.perform(put("/admin/categorias/{categoriaId}", 999999L)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maquiagem\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveValidarNomeNaEdicaoSemAlterarCategoria() throws Exception {
        removerCategoriasDeTeste();
        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder()
                .nome("Maquiagem").descricao("Descrição original").build());
        String nomeLongo = "x".repeat(101);

        for (String corpo : List.of("{}", "{\"nome\":null}", "{\"nome\":\"   \"}",
                "{\"nome\":\"%s\"}".formatted(nomeLongo))) {
            mockMvc.perform(put("/admin/categorias/{categoriaId}", categoria.getId())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isBadRequest());
        }

        Categoria preservada = categoriaRepository.findById(categoria.getId()).orElseThrow();
        assertThat(preservada.getNome()).isEqualTo("Maquiagem");
        assertThat(preservada.getDescricao()).isEqualTo("Descrição original");
    }

    @Test
    void deveProtegerEdicaoDeCategoria() throws Exception {
        String corpo = "{\"nome\":\"Maquiagem\"}";

        mockMvc.perform(put("/admin/categorias/{categoriaId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/admin/categorias/{categoriaId}", 1L)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROVIDER")))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }

    private void removerCategoriasDeTeste() {
        // Isola a massa somente no PostgreSQL do Testcontainers; a transação é revertida após cada teste.
        jdbcTemplate.update("UPDATE servicos SET categoria_id = NULL");
        categoriaRepository.deleteAllInBatch();
    }
}
