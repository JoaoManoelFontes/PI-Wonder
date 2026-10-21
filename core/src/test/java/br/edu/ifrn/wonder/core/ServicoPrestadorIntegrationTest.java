package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServicoPrestadorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired private PrestadorRepository prestadorRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ServicoRepository servicoRepository;
    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDENTE")
    void deveCriarEditarEInativarServicoDoDonoEAtualizarCategoriasDerivadas(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(situacao);
        Categoria categoria = criarCategoria();
        String corpo = mockMvc.perform(autenticada("POST", prestador, 0L)
                        .content(jsonMapper.writeValueAsString(Map.of("nome", "Corte", "preco", 30,
                                "duracao_min", 30, "categoria_id", categoria.getId(),
                                "status", "INATIVO", "prestador_id", Long.MAX_VALUE,
                                "foto_url", "https://cdn.wonder.dev/indevida.jpg"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(8)))
                .andExpect(jsonPath("$.prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$.categoria_id").value(categoria.getId().intValue()))
                .andExpect(jsonPath("$.nome").value("Corte"))
                .andExpect(jsonPath("$.preco").value(30))
                .andExpect(jsonPath("$.duracao_min").value(30))
                .andExpect(jsonPath("$.status").value("ativo"))
                .andExpect(jsonPath("$.foto_url").isEmpty())
                .andReturn().getResponse().getContentAsString();
        Long servicoId = ((Number) jsonMapper.readValue(corpo, Map.class).get("id")).longValue();
        Categoria outraCategoria = criarCategoria();

        mockMvc.perform(autenticada("PUT", prestador, servicoId)
                        .content(jsonMapper.writeValueAsString(Map.of("nome", "Servico atualizado", "preco", 45.50,
                                "duracao_min", 45, "categoria_id", outraCategoria.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Servico atualizado"))
                .andExpect(jsonPath("$.preco").value(45.50))
                .andExpect(jsonPath("$.duracao_min").value(45))
                .andExpect(jsonPath("$.categoria_id").value(outraCategoria.getId().intValue()));
        servicoRepository.flush();
        entityManager.clear();
        Servico salvo = servicoRepository.findById(servicoId).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Servico atualizado");
        assertThat(salvo.getPreco()).isEqualByComparingTo("45.50");
        assertThat(salvo.getDuracaoMin()).isEqualTo(45);
        assertThat(salvo.getCategoria().getId()).isEqualTo(outraCategoria.getId());

        mockMvc.perform(get("/catalogo/prestadores/me/categorias")
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(outraCategoria.getId().intValue()));

        mockMvc.perform(autenticada("DELETE", prestador, servicoId)).andExpect(status().isNoContent());
        servicoRepository.flush();
        entityManager.clear();
        assertThat(servicoRepository.findById(servicoId).orElseThrow().getStatus()).isEqualTo(StatusServico.INATIVO);
        mockMvc.perform(autenticada("DELETE", prestador, servicoId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/catalogo/prestadores/me/categorias")
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        if (situacao == StatusPrestador.ATIVO) {
            mockMvc.perform(get("/catalogo/prestadores/{id}/servicos", prestador.getId()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void deveExigirLogin(String metodo) throws Exception {
        mockMvc.perform(requisicao(metodo, 1L, 1L)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void deveBloquearOutroUsuarioMesmoComPapelAdmin(String metodo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador);
        mockMvc.perform(requisicao(metodo, prestador.getId(), servico.getId())
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        assertThat(servico.getNome()).isEqualTo("Servico original");
        assertThat(servico.getStatus()).isEqualTo(StatusServico.ATIVO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void deveBloquearAlteracoesDuranteAnaliseDoPrestador(String metodo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        Servico servico = criarServico(prestador);
        mockMvc.perform(autenticada(metodo, prestador, servico.getId())).andExpect(status().isConflict());
        assertThat(servico.getNome()).isEqualTo("Servico original");
        assertThat(servico.getStatus()).isEqualTo(StatusServico.ATIVO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void deveRetornar404ParaPrestadorInexistente(String metodo) throws Exception {
        mockMvc.perform(requisicao(metodo, Long.MAX_VALUE, Long.MAX_VALUE)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void deveRetornar404ParaServicoDeOutroPrestador(String metodo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico outroServico = criarServico(criarPrestador(StatusPrestador.ATIVO));
        mockMvc.perform(autenticada(metodo, prestador, outroServico.getId())).andExpect(status().isNotFound());
        assertThat(outroServico.getStatus()).isEqualTo(StatusServico.ATIVO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void deveRetornar404ParaServicoInexistente(String metodo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        mockMvc.perform(autenticada(metodo, prestador, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"POST,false", "POST,true", "PUT,false", "PUT,true"})
    void deveRejeitarCategoriaInexistenteOuInativa(String metodo, boolean existe) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador);
        Long categoriaId = Long.MAX_VALUE;
        if (existe) {
            Categoria categoria = criarCategoria();
            categoria.setStatus(StatusCategoria.INATIVA);
            categoriaRepository.flush();
            categoriaId = categoria.getId();
        }
        mockMvc.perform(autenticada(metodo, prestador, servico.getId())
                        .content(jsonMapper.writeValueAsString(Map.of("nome", "Servico alterado", "preco", 40,
                                "duracao_min", 30, "categoria_id", categoriaId))))
                .andExpect(status().isBadRequest());
        assertThat(servico.getNome()).isEqualTo("Servico original");
    }

    @ParameterizedTest
    @MethodSource("entradasInvalidas")
    void deveValidarCamposNaCriacaoEEdicao(String metodo, String campo, Object valor) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        Servico servico = criarServico(prestador);
        Map<String, Object> dados = dadosValidos();
        dados.put(campo, valor);
        mockMvc.perform(autenticada(metodo, prestador, servico.getId())
                        .content(jsonMapper.writeValueAsString(dados)))
                .andExpect(status().isBadRequest());
        assertThat(servico.getNome()).isEqualTo("Servico original");
    }

    static Stream<Arguments> entradasInvalidas() {
        return Stream.of("POST", "PUT").flatMap(metodo -> Stream.of(
                Arguments.of(metodo, "nome", ""), Arguments.of(metodo, "nome", "   "),
                Arguments.of(metodo, "nome", "x".repeat(151)),
                Arguments.of(metodo, "preco", new BigDecimal("-0.01")),
                Arguments.of(metodo, "preco", new BigDecimal("100000000.00")),
                Arguments.of(metodo, "preco", new BigDecimal("0.001")),
                Arguments.of(metodo, "duracao_min", 0), Arguments.of(metodo, "duracao_min", -1),
                Arguments.of(metodo, "categoria_id", 0), Arguments.of(metodo, "categoria_id", -1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "preco", "duracao_min"})
    void deveExigirCamposObrigatoriosNaCriacao(String campo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        Map<String, Object> dados = dadosValidos();
        dados.remove(campo);
        mockMvc.perform(autenticada("POST", prestador, 0L).content(jsonMapper.writeValueAsString(dados)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void devePermitirPrecoZeroEDuracaoMinimaSemCategoria() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        mockMvc.perform(autenticada("POST", prestador, 0L)
                        .content("{\"nome\":\"Servico gratuito\",\"preco\":0,\"duracao_min\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preco").value(0))
                .andExpect(jsonPath("$.duracao_min").value(1))
                .andExpect(jsonPath("$.categoria_id").isEmpty());
    }

    @Test
    void devePreservarCamposOmitidosNulosEProtegidosNaEdicao() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        Servico servico = criarServico(prestador);
        servico.setStatus(StatusServico.INATIVO);
        servico.setFotoUrl("https://cdn.wonder.dev/original.jpg");
        mockMvc.perform(autenticada("PUT", prestador, servico.getId()).content("""
                        {"preco":45.50,"nome":null,"duracao_min":null,"categoria_id":null,
                         "status":"ATIVO","prestador_id":999999,"foto_url":"foto-indevida"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Servico original"))
                .andExpect(jsonPath("$.preco").value(45.50))
                .andExpect(jsonPath("$.duracao_min").value(30))
                .andExpect(jsonPath("$.categoria_id").value(servico.getCategoria().getId().intValue()))
                .andExpect(jsonPath("$.prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$.status").value("inativo"))
                .andExpect(jsonPath("$.foto_url").value("https://cdn.wonder.dev/original.jpg"));
    }

    @ParameterizedTest
    @EnumSource(StatusAgendamento.class)
    void devePreservarHistoricoEBloquearInativacaoComAgendamentoAberto(StatusAgendamento situacao) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador);
        Agendamento agendamento = criarAgendamento(servico, situacao);
        Long servicoId = servico.getId();
        Long agendamentoId = agendamento.getId();
        boolean aberto = situacao == StatusAgendamento.PENDENTE || situacao == StatusAgendamento.CONFIRMADO;

        mockMvc.perform(autenticada("DELETE", prestador, servicoId))
                .andExpect(aberto ? status().isConflict() : status().isNoContent());
        servicoRepository.flush();
        entityManager.clear();
        assertThat(servicoRepository.findById(servicoId).orElseThrow().getStatus())
                .isEqualTo(aberto ? StatusServico.ATIVO : StatusServico.INATIVO);
        Agendamento salvo = agendamentoRepository.findById(agendamentoId).orElseThrow();
        assertThat(salvo.getServico().getId()).isEqualTo(servicoId);
        assertThat(salvo.getStatus()).isEqualTo(situacao);
        assertThat(salvo.getInicio()).isEqualTo(LocalDateTime.of(2099, 1, 5, 10, 0));
        assertThat(salvo.getFim()).isEqualTo(LocalDateTime.of(2099, 1, 5, 10, 30));
    }

    @Test
    void agendamentoDeOutroServicoNaoDeveBloquearInativacao() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador);
        Servico outroServico = criarServico(prestador);
        criarAgendamento(outroServico, StatusAgendamento.CONFIRMADO);
        mockMvc.perform(autenticada("DELETE", prestador, servico.getId())).andExpect(status().isNoContent());
        assertThat(servico.getStatus()).isEqualTo(StatusServico.INATIVO);
        assertThat(outroServico.getStatus()).isEqualTo(StatusServico.ATIVO);
    }

    private MockHttpServletRequestBuilder requisicao(String metodo, Long prestadorId, Long servicoId) {
        String caminho = "/catalogo/prestadores/" + prestadorId + "/servicos";
        if (!metodo.equals("POST")) {
            caminho += "/" + servicoId;
        }
        return request(HttpMethod.valueOf(metodo), caminho).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Servico atualizado\",\"preco\":40,\"duracao_min\":45}");
    }

    private MockHttpServletRequestBuilder autenticada(String metodo, Prestador prestador, Long servicoId) {
        return requisicao(metodo, prestador.getId(), servicoId)
                .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString())));
    }

    private Map<String, Object> dadosValidos() {
        return new HashMap<>(Map.of("nome", "Servico atualizado", "preco", 40, "duracao_min", 45));
    }

    private Prestador criarPrestador(StatusPrestador situacao) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder().perfilUsuario(perfil).nomeEstab("Studio servicos proprios")
                .status(situacao).ativo(situacao == StatusPrestador.ATIVO).build());
    }

    private Categoria criarCategoria() {
        return categoriaRepository.save(Categoria.builder().nome("Categoria servico " + UUID.randomUUID())
                .status(StatusCategoria.ATIVA).build());
    }

    private Servico criarServico(Prestador prestador) {
        return servicoRepository.save(Servico.builder().prestador(prestador).categoria(criarCategoria())
                .nome("Servico original").preco(new BigDecimal("30.00")).duracaoMin(30).status(StatusServico.ATIVO).build());
    }

    private Agendamento criarAgendamento(Servico servico, StatusAgendamento situacao) {
        PerfilUsuario cliente = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return agendamentoRepository.save(Agendamento.builder().cliente(cliente).prestador(servico.getPrestador())
                .servico(servico).inicio(LocalDateTime.of(2099, 1, 5, 10, 0))
                .fim(LocalDateTime.of(2099, 1, 5, 10, 30)).status(situacao).build());
    }
}
