package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.AvaliacaoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CadastroAvaliacaoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private AvaliacaoRepository avaliacaoRepository;
    @Autowired private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired private PrestadorRepository prestadorRepository;
    @Autowired private ServicoRepository servicoRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void clienteDeveCriarAvaliacaoComContratoPublicoESemAlterarAgendamento(int nota) throws Exception {
        Agendamento agendamento = criarAgendamento();
        mockMvc.perform(post(rota(agendamento)).with(jwt().jwt(token -> token.subject(cliente(agendamento))))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), nota)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.agendamento_id").value(agendamento.getId().intValue()))
                .andExpect(jsonPath("$.prestador_id").value(agendamento.getPrestador().getId().intValue()))
                .andExpect(jsonPath("$.nota").value(nota));

        assertThat(avaliacaoRepository.findByAgendamentoPrestadorIdOrderByIdAsc(agendamento.getPrestador().getId()))
                .singleElement().satisfies(avaliacao -> assertThat(avaliacao.getNota()).isEqualTo(nota));
        assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.CONCLUIDO);
        mockMvc.perform(get(rota(agendamento))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nota").value(nota));
    }

    @Test
    void deveExigirLogin() throws Exception {
        Agendamento agendamento = criarAgendamento();
        mockMvc.perform(post(rota(agendamento)).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(agendamento.getId(), 5)))
                .andExpect(status().isUnauthorized());
        assertSemAvaliacao(agendamento);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente-alheio", "dono", "admin"})
    void deveNegarAvaliacaoDeTerceiroMesmoDonoOuAdministrador(String autor) throws Exception {
        Agendamento agendamento = criarAgendamento();
        String identificador = autor.equals("dono")
                ? agendamento.getPrestador().getPerfilUsuario().getKeycloakId().toString() : UUID.randomUUID().toString();
        mockMvc.perform(post(rota(agendamento)).with(jwt().jwt(token -> token.subject(identificador))
                        .authorities(new SimpleGrantedAuthority(autor.equals("admin") ? "ROLE_ADMIN" : "ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), 5)))
                .andExpect(status().isForbidden());
        assertSemAvaliacao(agendamento);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAgendamento.class, mode = EnumSource.Mode.EXCLUDE, names = "CONCLUIDO")
    void deveRecusarAgendamentoNaoConcluido(StatusAgendamento situacao) throws Exception {
        Agendamento agendamento = criarAgendamento();
        agendamento.setStatus(situacao);
        agendamentoRepository.flush();
        mockMvc.perform(post(rota(agendamento)).with(jwt().jwt(token -> token.subject(cliente(agendamento))))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), 5)))
                .andExpect(status().isConflict());
        assertSemAvaliacao(agendamento);
    }

    @Test
    void deveImpedirDuplicidadeSemSubstituirNotaOriginal() throws Exception {
        Agendamento agendamento = criarAgendamento();
        mockMvc.perform(post(rota(agendamento)).with(jwt().jwt(token -> token.subject(cliente(agendamento))))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), 4)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(rota(agendamento)).with(jwt().jwt(token -> token.subject(cliente(agendamento))))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), 1)))
                .andExpect(status().isConflict());
        assertThat(avaliacaoRepository.findByAgendamentoPrestadorIdOrderByIdAsc(agendamento.getPrestador().getId()))
                .singleElement().satisfies(avaliacao -> assertThat(avaliacao.getNota()).isEqualTo(4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nota\":5}", "{\"agendamento_id\":1}",
            "{\"agendamento_id\":0,\"nota\":5}", "{\"agendamento_id\":-1,\"nota\":5}",
            "{\"agendamento_id\":1,\"nota\":0}", "{\"agendamento_id\":1,\"nota\":6}",
            "{\"agendamento_id\":null,\"nota\":null}", "{\"agendamento_id\":\"invalido\",\"nota\":5}"})
    void deveValidarCorpoSemPersistir(String corpo) throws Exception {
        long quantidade = avaliacaoRepository.count();
        mockMvc.perform(post("/catalogo/prestadores/1/avaliacoes").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest());
        assertThat(avaliacaoRepository.count()).isEqualTo(quantidade);
    }

    @Test
    void deveRecusarAgendamentoDeOutroPrestador() throws Exception {
        Agendamento agendamento = criarAgendamento();
        Agendamento outro = criarAgendamento();
        mockMvc.perform(post(rota(outro)).with(jwt().jwt(token -> token.subject(cliente(agendamento))))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), 5)))
                .andExpect(status().isNotFound());
        assertSemAvaliacao(agendamento);
    }

    @Test
    void deveRetornar404ParaAgendamentoOuPrestadorInexistente() throws Exception {
        Agendamento agendamento = criarAgendamento();
        mockMvc.perform(post(rota(agendamento)).with(jwt().jwt(token -> token.subject(cliente(agendamento))))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(Long.MAX_VALUE, 5)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/catalogo/prestadores/{id}/avaliacoes", Long.MAX_VALUE)
                        .with(jwt().jwt(token -> token.subject(cliente(agendamento))))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), 5)))
                .andExpect(status().isNotFound());
        assertSemAvaliacao(agendamento);
    }

    @Test
    void deveRetornar400ParaPrestadorInvalido() throws Exception {
        mockMvc.perform(post("/catalogo/prestadores/invalido/avaliacoes").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(1L, 5)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar401ParaIdentificadorJwtInvalido() throws Exception {
        Agendamento agendamento = criarAgendamento();
        mockMvc.perform(post(rota(agendamento)).with(jwt().jwt(token -> token.subject("invalido")))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), 5)))
                .andExpect(status().isUnauthorized());
        assertSemAvaliacao(agendamento);
    }

    @Test
    void devePermitirAvaliarAtendimentoConcluidoMesmoAposInativacao() throws Exception {
        Agendamento agendamento = criarAgendamento();
        agendamento.getPrestador().setAtivo(false);
        agendamento.getPrestador().setStatus(StatusPrestador.SUSPENSO);
        agendamento.getServico().setStatus(StatusServico.INATIVO);
        agendamentoRepository.flush();
        mockMvc.perform(post(rota(agendamento)).with(jwt().jwt(token -> token.subject(cliente(agendamento))))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), 5)))
                .andExpect(status().isCreated());
        mockMvc.perform(get(rota(agendamento))).andExpect(status().isNotFound());
        assertThat(agendamento.getPrestador().getStatus()).isEqualTo(StatusPrestador.SUSPENSO);
        assertThat(agendamento.getServico().getStatus()).isEqualTo(StatusServico.INATIVO);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void requisicoesSimultaneasDevemCriarSomenteUmaAvaliacao() throws Exception {
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        Agendamento agendamento = transacao.execute(situacao -> criarAgendamento());
        String identificador = cliente(agendamento);
        CountDownLatch inicio = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> enviar = () -> {
                if (!inicio.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Tempo excedido aguardando início das requisições");
                }
                return mockMvc.perform(post(rota(agendamento)).with(jwt().jwt(token -> token.subject(identificador)))
                                .contentType(MediaType.APPLICATION_JSON).content(corpo(agendamento.getId(), 5)))
                        .andReturn().getResponse().getStatus();
            };
            var primeira = executor.submit(enviar);
            var segunda = executor.submit(enviar);
            inicio.countDown();
            assertThat(java.util.List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM avaliacoes WHERE agendamento_id = ?",
                    Long.class, agendamento.getId())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            transacao.executeWithoutResult(situacao -> {
                jdbcTemplate.update("DELETE FROM avaliacoes WHERE agendamento_id = ?", agendamento.getId());
                agendamentoRepository.deleteById(agendamento.getId());
                agendamentoRepository.flush();
                servicoRepository.deleteById(agendamento.getServico().getId());
                servicoRepository.flush();
                prestadorRepository.deleteById(agendamento.getPrestador().getId());
                prestadorRepository.flush();
                perfilUsuarioRepository.deleteById(agendamento.getCliente().getId());
                perfilUsuarioRepository.deleteById(agendamento.getPrestador().getPerfilUsuario().getId());
            });
        }
    }

    private Agendamento criarAgendamento() {
        PerfilUsuario dono = perfilUsuarioRepository.saveAndFlush(new PerfilUsuario(UUID.randomUUID()));
        PerfilUsuario cliente = perfilUsuarioRepository.saveAndFlush(new PerfilUsuario(UUID.randomUUID()));
        Prestador prestador = prestadorRepository.saveAndFlush(Prestador.builder().perfilUsuario(dono)
                .nomeEstab("Studio avaliações").status(StatusPrestador.ATIVO).ativo(true).build());
        Servico servico = servicoRepository.saveAndFlush(Servico.builder().prestador(prestador).nome("Corte")
                .preco(new BigDecimal("40.00")).duracaoMin(30).build());
        LocalDateTime inicio = LocalDateTime.of(2030, 1, 7, 9, 0);
        return agendamentoRepository.saveAndFlush(Agendamento.builder().cliente(cliente).prestador(prestador)
                .servico(servico).inicio(inicio).fim(inicio.plusMinutes(30)).status(StatusAgendamento.CONCLUIDO).build());
    }

    private void assertSemAvaliacao(Agendamento agendamento) {
        assertThat(avaliacaoRepository.existsByAgendamentoId(agendamento.getId())).isFalse();
    }

    private String cliente(Agendamento agendamento) {
        return agendamento.getCliente().getKeycloakId().toString();
    }

    private String rota(Agendamento agendamento) {
        return "/catalogo/prestadores/%s/avaliacoes".formatted(agendamento.getPrestador().getId());
    }

    private String corpo(Long agendamentoId, int nota) {
        return "{\"agendamento_id\":%s,\"nota\":%s}".formatted(agendamentoId, nota);
    }
}
