package br.edu.ifrn.wonder.core;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Categoria;
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
class CatalogoPrestadoresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrestadorRepository prestadorRepository;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ServicoRepository servicoRepository;

    @Test
    void deveListarSemLoginSomentePrestadoresAtivosEAprovadosOrdenadosComContratoPublico() throws Exception {
        inativarPrestadoresExistentes();
        Prestador ultimo = criarPrestador("Z Studio publico", StatusPrestador.ATIVO, true);
        Prestador primeiro = criarPrestador("A Studio publico", StatusPrestador.ATIVO, true);
        primeiro.setDocumento("00000000000191");
        primeiro.setEndereco("Rua das Flores");
        primeiro.setNumero("100");
        primeiro.setBairro("Centro");
        primeiro.setCidade("Natal");
        primeiro.setEstado("RN");
        primeiro.setComplemento("Sala 1");
        primeiro.setFotoUrl("https://cdn.wonder.dev/studio.jpg");

        for (StatusPrestador status : StatusPrestador.values()) {
            for (boolean ativo : new boolean[] {false, true}) {
                if (status == StatusPrestador.ATIVO && ativo) {
                    continue;
                }
                criarPrestador("Studio oculto " + status + ativo, status, ativo);
            }
        }
        prestadorRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]", aMapWithSize(13)))
                .andExpect(jsonPath("$[0].id").value(primeiro.getId().intValue()))
                .andExpect(jsonPath("$[0].perfilUsuarioId").value(primeiro.getPerfilUsuario().getId().toString()))
                .andExpect(jsonPath("$[0].nomeEstab").value("A Studio publico"))
                .andExpect(jsonPath("$[0].documento").value("00000000000191"))
                .andExpect(jsonPath("$[0].endereco").value("Rua das Flores"))
                .andExpect(jsonPath("$[0].numero").value("100"))
                .andExpect(jsonPath("$[0].bairro").value("Centro"))
                .andExpect(jsonPath("$[0].cidade").value("Natal"))
                .andExpect(jsonPath("$[0].estado").value("RN"))
                .andExpect(jsonPath("$[0].complemento").value("Sala 1"))
                .andExpect(jsonPath("$[0].status").value("ativo"))
                .andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[0].fotoUrl").value("https://cdn.wonder.dev/studio.jpg"))
                .andExpect(jsonPath("$[1].id").value(ultimo.getId().intValue()))
                .andExpect(jsonPath("$[1].nomeEstab").value("Z Studio publico"))
                .andExpect(jsonPath("$[1].status").value("ativo"))
                .andExpect(jsonPath("$[1].ativo").value(true))
                .andExpect(jsonPath("$[1].fotoUrl").isEmpty());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistemPrestadoresPublicaveis() throws Exception {
        inativarPrestadoresExistentes();

        mockMvc.perform(get("/catalogo/prestadores"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deveManterAutenticacaoNasDemaisRotasEMetodos() throws Exception {
        mockMvc.perform(get("/perfis/eu"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/catalogo/prestadores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveFiltrarNomeParcialSemDiferenciarMaiusculasEIgnorandoEspacosNasExtremidades() throws Exception {
        inativarPrestadoresExistentes();
        criarPrestador("Z Studio Beleza", StatusPrestador.ATIVO, true);
        criarPrestador("A Studio Beleza", StatusPrestador.ATIVO, true);
        criarPrestador("Studio Diferente", StatusPrestador.ATIVO, true);
        criarPrestador("Studio Beleza pendente", StatusPrestador.PENDENTE, true);
        criarPrestador("Studio Beleza inativo", StatusPrestador.ATIVO, false);
        prestadorRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores").param("nome", "  sTuDiO bElE  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nomeEstab").value("A Studio Beleza"))
                .andExpect(jsonPath("$[1].nomeEstab").value("Z Studio Beleza"));
    }

    @Test
    void deveIgnorarFiltroDeNomeVazioOuSomenteComEspacos() throws Exception {
        inativarPrestadoresExistentes();
        Prestador prestador = criarPrestador("Studio publico", StatusPrestador.ATIVO, true);
        prestadorRepository.flush();

        for (String nome : new String[] {"", "   "}) {
            mockMvc.perform(get("/catalogo/prestadores").param("nome", nome))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(prestador.getId().intValue()));
        }
    }

    @Test
    void deveFiltrarCategoriaPorServicosAtivosSemDuplicarPrestadores() throws Exception {
        inativarPrestadoresExistentes();
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Categoria filtro").build());
        Categoria outra = categoriaRepository.save(Categoria.builder().nome("Outra categoria filtro").build());
        Prestador ultimo = criarPrestador("Z Studio categoria", StatusPrestador.ATIVO, true);
        Prestador primeiro = criarPrestador("A Studio categoria", StatusPrestador.ATIVO, true);
        criarServico(ultimo, categoria, StatusServico.ATIVO);
        criarServico(primeiro, categoria, StatusServico.ATIVO);
        criarServico(primeiro, categoria, StatusServico.ATIVO);
        criarPrestador("Studio sem servicos", StatusPrestador.ATIVO, true);
        criarServico(criarPrestador("Studio outra categoria", StatusPrestador.ATIVO, true), outra, StatusServico.ATIVO);
        criarServico(criarPrestador("Studio servico inativo", StatusPrestador.ATIVO, true), categoria, StatusServico.INATIVO);
        criarServico(criarPrestador("Studio pendente", StatusPrestador.PENDENTE, true), categoria, StatusServico.ATIVO);
        criarServico(criarPrestador("Studio desativado", StatusPrestador.ATIVO, false), categoria, StatusServico.ATIVO);
        criarServico(criarPrestador("Studio sem categoria", StatusPrestador.ATIVO, true), null, StatusServico.ATIVO);
        servicoRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores").param("categoria_id", categoria.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(primeiro.getId().intValue()))
                .andExpect(jsonPath("$[1].id").value(ultimo.getId().intValue()));

        categoria.setStatus(StatusCategoria.INATIVA);
        categoriaRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores").param("categoria_id", categoria.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deveCombinarNomeECategoriaERetornarListaVaziaQuandoNaoHaCorrespondencia() throws Exception {
        inativarPrestadoresExistentes();
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Categoria combinada").build());
        Categoria outra = categoriaRepository.save(Categoria.builder().nome("Outra categoria combinada").build());
        Prestador encontrado = criarPrestador("Studio Alpha", StatusPrestador.ATIVO, true);
        criarServico(encontrado, categoria, StatusServico.ATIVO);
        criarServico(criarPrestador("Studio Beta", StatusPrestador.ATIVO, true), categoria, StatusServico.ATIVO);
        criarServico(criarPrestador("Studio Alpha outra", StatusPrestador.ATIVO, true), outra, StatusServico.ATIVO);
        servicoRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores")
                        .param("nome", "alpha").param("categoria_id", categoria.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(encontrado.getId().intValue()));
        mockMvc.perform(get("/catalogo/prestadores").param("nome", "sem correspondencia"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/catalogo/prestadores").param("categoria_id", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deveRetornar400QuandoCategoriaIdNaoEhNumerico() throws Exception {
        mockMvc.perform(get("/catalogo/prestadores").param("categoria_id", "invalido"))
                .andExpect(status().isBadRequest());
    }

    private void criarServico(Prestador prestador, Categoria categoria, StatusServico status) {
        servicoRepository.save(Servico.builder()
                .prestador(prestador).categoria(categoria).nome("Servico filtro")
                .preco(new BigDecimal("30.00")).duracaoMin(30).status(status).build());
    }

    private void inativarPrestadoresExistentes() {
        prestadorRepository.findAll().forEach(prestador -> prestador.setAtivo(false));
        prestadorRepository.flush();
    }

    private Prestador criarPrestador(String nome, StatusPrestador status, boolean ativo) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder()
                .perfilUsuario(perfil).nomeEstab(nome).status(status).ativo(ativo).build());
    }
}
