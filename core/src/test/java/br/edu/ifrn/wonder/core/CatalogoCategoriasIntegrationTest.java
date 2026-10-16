package br.edu.ifrn.wonder.core;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoCategoriasIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    void deveListarSemLoginSomenteCategoriasAtivasOrdenadasComContratoPublico() throws Exception {
        inativarCategoriasExistentes();
        Categoria ultima = categoriaRepository.save(Categoria.builder()
                .nome("Z Categoria publica").build());
        Categoria primeira = categoriaRepository.save(Categoria.builder()
                .nome("A Categoria publica")
                .descricao("Cuidados de beleza")
                .fotoUrl("https://cdn.wonder.dev/categoria.jpg")
                .build());
        categoriaRepository.saveAndFlush(Categoria.builder()
                .nome("Categoria oculta").status(StatusCategoria.INATIVA).build());

        mockMvc.perform(get("/catalogo/categorias"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)))
                .andExpect(jsonPath("$[0].id").value(primeira.getId().intValue()))
                .andExpect(jsonPath("$[0].nome").value("A Categoria publica"))
                .andExpect(jsonPath("$[0].descricao").value("Cuidados de beleza"))
                .andExpect(jsonPath("$[0].status").value("ativa"))
                .andExpect(jsonPath("$[0].foto_url").value("https://cdn.wonder.dev/categoria.jpg"))
                .andExpect(jsonPath("$[1].id").value(ultima.getId().intValue()))
                .andExpect(jsonPath("$[1].nome").value("Z Categoria publica"))
                .andExpect(jsonPath("$[1].status").value("ativa"))
                .andExpect(jsonPath("$[1].foto_url").isEmpty());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistemCategoriasAtivas() throws Exception {
        inativarCategoriasExistentes();

        mockMvc.perform(get("/catalogo/categorias"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deveManterAutenticacaoNasDemaisRotasEMetodos() throws Exception {
        mockMvc.perform(get("/perfis/eu"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/catalogo/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private void inativarCategoriasExistentes() {
        categoriaRepository.findAll().forEach(categoria -> categoria.setStatus(StatusCategoria.INATIVA));
        categoriaRepository.flush();
    }
}
