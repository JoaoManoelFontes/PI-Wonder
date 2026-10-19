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
class CatalogoServicosIntegrationTest {

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
    void deveListarSemLoginSomenteServicosAtivosDoPrestadorOrdenadosPorNome() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, true);
        Categoria categoria = categoriaRepository.save(Categoria.builder()
                .nome("Categoria de servicos")
                .status(StatusCategoria.ATIVA)
                .build());
        Servico ultimo = criarServico(prestador, categoria, "Z Servico", StatusServico.ATIVO);
        Servico primeiro = criarServico(prestador, categoria, "A Servico", StatusServico.ATIVO);
        primeiro.setFotoUrl("https://cdn.wonder.dev/servico.jpg");
        criarServico(prestador, categoria, "Servico inativo", StatusServico.INATIVO);
        criarServico(criarPrestador(StatusPrestador.ATIVO, true), categoria,
                "Servico de outro prestador", StatusServico.ATIVO);
        servicoRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/servicos", prestador.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]", aMapWithSize(8)))
                .andExpect(jsonPath("$[0].id").value(primeiro.getId().intValue()))
                .andExpect(jsonPath("$[0].prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$[0].categoria_id").value(categoria.getId().intValue()))
                .andExpect(jsonPath("$[0].nome").value("A Servico"))
                .andExpect(jsonPath("$[0].preco").value(30.0))
                .andExpect(jsonPath("$[0].duracao_min").value(30))
                .andExpect(jsonPath("$[0].status").value("ativo"))
                .andExpect(jsonPath("$[0].foto_url").value("https://cdn.wonder.dev/servico.jpg"))
                .andExpect(jsonPath("$[1].id").value(ultimo.getId().intValue()))
                .andExpect(jsonPath("$[1].nome").value("Z Servico"));
    }

    @Test
    void deveRetornarListaVaziaQuandoPrestadorPublicoNaoTemServicosAtivos() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, true);
        criarServico(prestador, null, "Servico inativo", StatusServico.INATIVO);
        servicoRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/servicos", prestador.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deveRetornar404QuandoPrestadorNaoExiste() throws Exception {
        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/servicos", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar404ParaPrestadoresNaoPublicaveisMesmoComServicoAtivo() throws Exception {
        for (StatusPrestador statusPrestador : StatusPrestador.values()) {
            for (boolean ativo : new boolean[] {false, true}) {
                if (statusPrestador == StatusPrestador.ATIVO && ativo) {
                    continue;
                }
                Prestador prestador = criarPrestador(statusPrestador, ativo);
                criarServico(prestador, null, "Servico oculto", StatusServico.ATIVO);
                servicoRepository.flush();

                mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/servicos", prestador.getId()))
                        .andExpect(status().isNotFound());
            }
        }
    }

    private Prestador criarPrestador(StatusPrestador status, boolean ativo) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder()
                .perfilUsuario(perfil)
                .nomeEstab("Studio servicos")
                .status(status)
                .ativo(ativo)
                .build());
    }

    private Servico criarServico(
            Prestador prestador,
            Categoria categoria,
            String nome,
            StatusServico status) {
        return servicoRepository.save(Servico.builder()
                .prestador(prestador)
                .categoria(categoria)
                .nome(nome)
                .preco(new BigDecimal("30.00"))
                .duracaoMin(30)
                .status(status)
                .build());
    }
}
