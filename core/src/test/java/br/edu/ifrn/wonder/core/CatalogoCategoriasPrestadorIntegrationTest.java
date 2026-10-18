package br.edu.ifrn.wonder.core;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoCategoriasPrestadorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private PrestadorRepository prestadorRepository;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Autowired
    private ServicoRepository servicoRepository;

    @Test
    void deveListarSemLoginCategoriasAtivasDoPrestadorOrdenadasESemRepeticoes() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, true);
        Categoria ultima = criarCategoria("Z Categoria do prestador", StatusCategoria.ATIVA);
        Categoria primeira = criarCategoria("A Categoria do prestador", StatusCategoria.ATIVA);
        primeira.setDescricao("Cuidados de beleza");
        primeira.setFotoUrl("https://cdn.wonder.dev/categoria.jpg");
        criarServico(prestador, ultima, StatusServico.ATIVO);
        criarServico(prestador, primeira, StatusServico.ATIVO);
        criarServico(prestador, primeira, StatusServico.ATIVO);
        criarServico(prestador, criarCategoria("Categoria inativa", StatusCategoria.INATIVA), StatusServico.ATIVO);
        criarServico(prestador, criarCategoria("Categoria de servico inativo", StatusCategoria.ATIVA), StatusServico.INATIVO);
        criarServico(prestador, null, StatusServico.ATIVO);
        criarCategoria("Categoria sem vinculo", StatusCategoria.ATIVA);
        criarServico(criarPrestador(StatusPrestador.ATIVO, true),
                criarCategoria("Categoria de outro prestador", StatusCategoria.ATIVA), StatusServico.ATIVO);
        servicoRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/categorias", prestador.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)))
                .andExpect(jsonPath("$[0].id").value(primeira.getId().intValue()))
                .andExpect(jsonPath("$[0].nome").value("A Categoria do prestador"))
                .andExpect(jsonPath("$[0].descricao").value("Cuidados de beleza"))
                .andExpect(jsonPath("$[0].status").value("ativa"))
                .andExpect(jsonPath("$[0].foto_url").value("https://cdn.wonder.dev/categoria.jpg"))
                .andExpect(jsonPath("$[1].id").value(ultima.getId().intValue()))
                .andExpect(jsonPath("$[1].nome").value("Z Categoria do prestador"))
                .andExpect(jsonPath("$[1].status").value("ativa"))
                .andExpect(jsonPath("$[1].foto_url").isEmpty());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHaCategoriasPublicaveis() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, true);
        prestadorRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/categorias", prestador.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        criarServico(prestador, criarCategoria("Categoria oculta", StatusCategoria.INATIVA), StatusServico.ATIVO);
        criarServico(prestador, criarCategoria("Categoria indisponivel", StatusCategoria.ATIVA), StatusServico.INATIVO);
        criarServico(prestador, null, StatusServico.ATIVO);
        servicoRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/categorias", prestador.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deveRetornar404QuandoPrestadorNaoExiste() throws Exception {
        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/categorias", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar404ParaPrestadoresNaoPublicaveisMesmoComCategoriaAtiva() throws Exception {
        Categoria categoria = criarCategoria("Categoria prestador oculto", StatusCategoria.ATIVA);
        for (StatusPrestador status : StatusPrestador.values()) {
            for (boolean ativo : new boolean[] {false, true}) {
                if (status == StatusPrestador.ATIVO && ativo) {
                    continue;
                }
                Prestador prestador = criarPrestador(status, ativo);
                criarServico(prestador, categoria, StatusServico.ATIVO);
                servicoRepository.flush();

                mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/categorias", prestador.getId()))
                        .andExpect(status().isNotFound());
            }
        }
    }

    private Prestador criarPrestador(StatusPrestador status, boolean ativo) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder()
                .perfilUsuario(perfil).nomeEstab("Studio categorias").status(status).ativo(ativo).build());
    }

    private Categoria criarCategoria(String nome, StatusCategoria status) {
        return categoriaRepository.save(Categoria.builder().nome(nome).status(status).build());
    }

    private void criarServico(Prestador prestador, Categoria categoria, StatusServico status) {
        servicoRepository.save(Servico.builder()
                .prestador(prestador).categoria(categoria).nome("Servico de beleza")
                .preco(new BigDecimal("30.00")).duracaoMin(30).status(status).build());
    }
}
