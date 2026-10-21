package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
class CatalogoCategoriasPrestadorLogadoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private ServicoRepository servicoRepository;

    @Test
    void deveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/catalogo/prestadores/me/categorias"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveListarSomenteCategoriasAtivasDosServicosAtivosDoDonoOrdenadasESemDuplicacao() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Prestador outroPrestador = criarPrestador(StatusPrestador.ATIVO);
        Categoria primeira = criarCategoria("A Categoria", StatusCategoria.ATIVA);
        primeira.setDescricao("Descricao da categoria");
        primeira.setFotoUrl("https://cdn.wonder.dev/categoria.jpg");
        Categoria ultima = criarCategoria("Z Categoria", StatusCategoria.ATIVA);
        criarServico(prestador, ultima, StatusServico.ATIVO);
        criarServico(prestador, primeira, StatusServico.ATIVO);
        criarServico(prestador, primeira, StatusServico.ATIVO);
        criarServico(prestador, criarCategoria("Inativa", StatusCategoria.INATIVA), StatusServico.ATIVO);
        criarServico(prestador, criarCategoria("Servico inativo", StatusCategoria.ATIVA), StatusServico.INATIVO);
        criarServico(prestador, null, StatusServico.ATIVO);
        criarServico(outroPrestador, criarCategoria("Outro prestador", StatusCategoria.ATIVA), StatusServico.ATIVO);
        servicoRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores/me/categorias")
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString())))
                        .param("prestador_id", outroPrestador.getId().toString())
                        .param("perfilUsuarioId", outroPrestador.getPerfilUsuario().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)))
                .andExpect(jsonPath("$[0].id").value(primeira.getId().intValue()))
                .andExpect(jsonPath("$[0].nome").value(primeira.getNome()))
                .andExpect(jsonPath("$[0].descricao").value("Descricao da categoria"))
                .andExpect(jsonPath("$[0].status").value("ativa"))
                .andExpect(jsonPath("$[0].foto_url").value("https://cdn.wonder.dev/categoria.jpg"))
                .andExpect(jsonPath("$[1].id").value(ultima.getId().intValue()));
    }

    @ParameterizedTest
    @EnumSource(StatusPrestador.class)
    void devePermitirConsultaDoDonoIndependentementeDaPublicacao(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(situacao);
        Categoria categoria = criarCategoria("Categoria propria", StatusCategoria.ATIVA);
        criarServico(prestador, categoria, StatusServico.ATIVO);

        for (boolean ativo : new boolean[] {false, true}) {
            prestador.setAtivo(ativo);
            prestadorRepository.flush();
            mockMvc.perform(get("/catalogo/prestadores/me/categorias")
                            .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(categoria.getId().intValue()));
        }
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHaCategoriasPublicaveis() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        criarServico(prestador, criarCategoria("Sem servico ativo", StatusCategoria.ATIVA), StatusServico.INATIVO);
        criarServico(prestador, criarCategoria("Categoria inativa", StatusCategoria.INATIVA), StatusServico.ATIVO);
        criarServico(prestador, null, StatusServico.ATIVO);

        mockMvc.perform(get("/catalogo/prestadores/me/categorias")
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveRetornar404SemCriarPerfilQuandoUsuarioNaoPossuiPrestador(boolean possuiPerfil) throws Exception {
        UUID keycloakId = UUID.randomUUID();
        if (possuiPerfil) {
            perfilUsuarioRepository.save(new PerfilUsuario(keycloakId));
        }
        long quantidadePerfis = perfilUsuarioRepository.count();

        mockMvc.perform(get("/catalogo/prestadores/me/categorias")
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString()))))
                .andExpect(status().isNotFound());

        assertThat(perfilUsuarioRepository.count()).isEqualTo(quantidadePerfis);
    }

    @Test
    void deveDerivarCategoriasDasAlteracoesNosServicosSemAssociacaoManual() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        Categoria categoria = criarCategoria("Categoria derivada", StatusCategoria.ATIVA);
        Servico servico = criarServico(prestador, categoria, StatusServico.ATIVO);
        String keycloakId = prestador.getPerfilUsuario().getKeycloakId().toString();

        mockMvc.perform(get("/catalogo/prestadores/me/categorias")
                        .with(jwt().jwt(token -> token.subject(keycloakId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(categoria.getId().intValue()));

        servico.setStatus(StatusServico.INATIVO);
        servicoRepository.flush();
        mockMvc.perform(get("/catalogo/prestadores/me/categorias")
                        .with(jwt().jwt(token -> token.subject(keycloakId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        Categoria outraCategoria = criarCategoria("Outra categoria derivada", StatusCategoria.ATIVA);
        servico.setCategoria(outraCategoria);
        servico.setStatus(StatusServico.ATIVO);
        servicoRepository.flush();
        mockMvc.perform(get("/catalogo/prestadores/me/categorias")
                        .with(jwt().jwt(token -> token.subject(keycloakId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(outraCategoria.getId().intValue()));
    }

    private Prestador criarPrestador(StatusPrestador situacao) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder().perfilUsuario(perfil)
                .nomeEstab("Studio categorias proprias").status(situacao).ativo(false).build());
    }

    private Categoria criarCategoria(String nome, StatusCategoria situacao) {
        return categoriaRepository.save(Categoria.builder().nome(nome + " " + UUID.randomUUID())
                .status(situacao).build());
    }

    private Servico criarServico(Prestador prestador, Categoria categoria, StatusServico situacao) {
        return servicoRepository.save(Servico.builder().prestador(prestador).categoria(categoria)
                .nome("Servico categorias proprias").preco(new BigDecimal("30.00"))
                .duracaoMin(30).status(situacao).build());
    }
}
