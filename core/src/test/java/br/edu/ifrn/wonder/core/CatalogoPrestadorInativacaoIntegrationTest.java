package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.Avaliacao;
import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.dto.AtualizacaoPrestadorRequest;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.AvaliacaoRepository;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import br.edu.ifrn.wonder.core.service.PrestadorService;
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoPrestadorInativacaoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PrestadorRepository prestadorRepository;
    @Autowired private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private ServicoRepository servicoRepository;
    @Autowired private HorarioAtendimentoRepository horarioAtendimentoRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private AvaliacaoRepository avaliacaoRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PrestadorService prestadorService;

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDENTE")
    void donoDeveInativarSemAlterarCadastroOuAprovacao(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(situacao);
        Map<String, Object> anterior = cadastro(prestador.getId());
        String dono = dono(prestador);

        mockMvc.perform(delete(rota(prestador)).with(jwt().jwt(token -> token.subject(dono))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> atual = cadastro(prestador.getId());
        assertThat(atual.get("ativo")).isEqualTo(false);
        assertThat(atual).containsAllEntriesOf(semCamposDeInativacao(anterior));
        mockMvc.perform(get("/catalogo/prestadores/me").with(jwt().jwt(token -> token.subject(dono))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void devePreservarPerfilServicosCategoriasHorariosAgendamentosEAvaliacoes() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Agendamento concluido = criarAgendamento(prestador, StatusAgendamento.CONCLUIDO);
        Agendamento cancelado = criarAgendamento(prestador, StatusAgendamento.CANCELADO);
        Avaliacao avaliacao = avaliacaoRepository.saveAndFlush(Avaliacao.builder()
                .agendamento(concluido).nota(5).build());
        HorarioAtendimento horario = horarioAtendimentoRepository.saveAndFlush(HorarioAtendimento.builder()
                .prestador(prestador).diaSemana(1).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(18, 0)).build());
        Map<String, Object> perfilAntes = linha("perfis_usuarios", prestador.getPerfilUsuario().getId());
        Map<String, Object> servicoAntes = linha("servicos", concluido.getServico().getId());
        Map<String, Object> categoriaAntes = linha("categorias", concluido.getServico().getCategoria().getId());
        Map<String, Object> horarioAntes = linha("horarios_atendimento", horario.getId());
        Map<String, Object> concluidoAntes = linha("agendamentos", concluido.getId());
        Map<String, Object> canceladoAntes = linha("agendamentos", cancelado.getId());
        Map<String, Object> avaliacaoAntes = linha("avaliacoes", avaliacao.getId());

        mockMvc.perform(delete(rota(prestador)).with(jwt().jwt(token -> token.subject(dono(prestador)))))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(linha("perfis_usuarios", prestador.getPerfilUsuario().getId())).isEqualTo(perfilAntes);
        assertThat(linha("servicos", concluido.getServico().getId())).isEqualTo(servicoAntes);
        assertThat(linha("categorias", concluido.getServico().getCategoria().getId())).isEqualTo(categoriaAntes);
        assertThat(linha("horarios_atendimento", horario.getId())).isEqualTo(horarioAntes);
        assertThat(linha("agendamentos", concluido.getId())).isEqualTo(concluidoAntes);
        assertThat(linha("agendamentos", cancelado.getId())).isEqualTo(canceladoAntes);
        assertThat(linha("avaliacoes", avaliacao.getId())).isEqualTo(avaliacaoAntes);
        assertThat(cadastro(prestador.getId()).get("foto_url")).isEqualTo(prestador.getFotoUrl());

        for (String sufixo : new String[] {"", "/servicos", "/categorias", "/horarios", "/avaliacoes"}) {
            mockMvc.perform(get(rota(prestador) + sufixo)).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/catalogo/prestadores").param("nome", prestador.getNomeEstab()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/agendamentos/disponibilidade")
                        .param("prestador_id", prestador.getId().toString())
                        .param("servico_id", concluido.getServico().getId().toString()).param("data", "2030-01-07"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/agendamentos/dias-disponiveis")
                        .param("prestador_id", prestador.getId().toString())
                        .param("servico_id", concluido.getServico().getId().toString()).param("mes", "2030-01"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = StatusAgendamento.class, names = {"PENDENTE", "CONFIRMADO"})
    void deveBloquearQuandoQualquerServicoDoPrestadorPossuiAgendamentoAberto(StatusAgendamento situacao) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        criarAgendamento(prestador, StatusAgendamento.CONCLUIDO);
        Agendamento aberto = criarAgendamento(prestador, situacao);
        Map<String, Object> anterior = cadastro(prestador.getId());
        Map<String, Object> agendamentoAntes = linha("agendamentos", aberto.getId());

        mockMvc.perform(delete(rota(prestador)).with(jwt().jwt(token -> token.subject(dono(prestador)))))
                .andExpect(status().isConflict());
        entityManager.flush();
        assertThat(cadastro(prestador.getId())).isEqualTo(anterior);
        assertThat(linha("agendamentos", aberto.getId())).isEqualTo(agendamentoAntes);
    }

    @Test
    void agendamentosAbertosDeOutroPrestadorNaoDevemBloquear() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        criarAgendamento(criarPrestador(StatusPrestador.ATIVO), StatusAgendamento.CONFIRMADO);
        mockMvc.perform(delete(rota(prestador)).with(jwt().jwt(token -> token.subject(dono(prestador)))))
                .andExpect(status().isNoContent());
    }

    @Test
    void repetirInativacaoDeveRetornar204() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            mockMvc.perform(delete(rota(prestador)).with(jwt().jwt(token -> token.subject(dono(prestador)))))
                    .andExpect(status().isNoContent());
        }
        assertThat(prestadorRepository.findById(prestador.getId())).isPresent();
        assertThat(prestador.isAtivo()).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void edicaoSimultaneaNaoDeveRepublicarCadastroInativado() throws Exception {
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        Prestador prestador = transacao.execute(situacao -> criarPrestador(StatusPrestador.ATIVO));
        UUID keycloakId = prestador.getPerfilUsuario().getKeycloakId();
        CountDownLatch edicaoPronta = new CountDownLatch(1);
        CountDownLatch liberarEdicao = new CountDownLatch(1);
        CountDownLatch inativacaoIniciada = new CountDownLatch(1);
        AtomicInteger conexaoInativacao = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var edicao = executor.submit(() -> transacao.executeWithoutResult(situacao -> {
                prestadorService.atualizarPrestador(prestador.getId(), keycloakId,
                        new AtualizacaoPrestadorRequest("Nome atualizado", null, null, null, null, null, null, null));
                edicaoPronta.countDown();
                try {
                    if (!liberarEdicao.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Tempo excedido aguardando liberação da edição");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(edicaoPronta.await(30, TimeUnit.SECONDS)).isTrue();
            var inativacao = executor.submit(() -> transacao.executeWithoutResult(situacao -> {
                conexaoInativacao.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                inativacaoIniciada.countDown();
                prestadorService.inativar(prestador.getId(), keycloakId);
            }));
            assertThat(inativacaoIniciada.await(30, TimeUnit.SECONDS)).isTrue();

            // Aguarda o bloqueio real no PostgreSQL, sem depender da velocidade das threads.
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            boolean aguardandoBloqueio = false;
            while (!inativacao.isDone() && System.nanoTime() < limite) {
                aguardandoBloqueio = "Lock".equals(jdbcTemplate.queryForObject(
                        "SELECT wait_event_type FROM pg_stat_activity WHERE pid = ?", String.class,
                        conexaoInativacao.get()));
                if (aguardandoBloqueio) {
                    break;
                }
                Thread.sleep(20);
            }
            assertThat(aguardandoBloqueio || inativacao.isDone()).isTrue();
            liberarEdicao.countDown();
            edicao.get(30, TimeUnit.SECONDS);
            inativacao.get(30, TimeUnit.SECONDS);

            assertThat(cadastro(prestador.getId())).containsEntry("ativo", false)
                    .containsEntry("nome_estab", "Nome atualizado");
        } finally {
            liberarEdicao.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            transacao.executeWithoutResult(situacao -> {
                prestadorRepository.deleteById(prestador.getId());
                prestadorRepository.flush();
                perfilUsuarioRepository.deleteById(prestador.getPerfilUsuario().getId());
            });
        }
    }

    @Test
    void cadastroPendenteNaoPodeSerInativadoDuranteAnalise() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        prestador.setAtivo(false);
        prestadorRepository.flush();
        Map<String, Object> anterior = cadastro(prestador.getId());
        mockMvc.perform(delete(rota(prestador)).with(jwt().jwt(token -> token.subject(dono(prestador)))))
                .andExpect(status().isConflict());
        entityManager.flush();
        assertThat(cadastro(prestador.getId())).isEqualTo(anterior);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_ADMIN"})
    void deveNegarInativacaoDeTerceiroMesmoAdministrador(String papel) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Map<String, Object> anterior = cadastro(prestador.getId());
        mockMvc.perform(delete(rota(prestador)).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority(papel))))
                .andExpect(status().isForbidden());
        entityManager.flush();
        assertThat(cadastro(prestador.getId())).isEqualTo(anterior);
    }

    @Test
    void deveExigirLogin() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        mockMvc.perform(delete(rota(prestador))).andExpect(status().isUnauthorized());
        assertThat(prestador.isAtivo()).isTrue();
    }

    @Test
    void deveRetornar404ParaPrestadorInexistente() throws Exception {
        mockMvc.perform(delete("/catalogo/prestadores/{id}", Long.MAX_VALUE)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalido", "9999999999999999999999999"})
    void deveRetornar400ParaIdInvalido(String id) throws Exception {
        mockMvc.perform(delete("/catalogo/prestadores/{id}", id).with(jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar401ParaIdentificadorJwtInvalido() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        mockMvc.perform(delete(rota(prestador)).with(jwt().jwt(token -> token.subject("invalido"))))
                .andExpect(status().isUnauthorized());
        assertThat(prestador.isAtivo()).isTrue();
    }

    private Prestador criarPrestador(StatusPrestador situacao) {
        PerfilUsuario perfil = perfilUsuarioRepository.saveAndFlush(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.saveAndFlush(Prestador.builder().perfilUsuario(perfil)
                .nomeEstab("Studio " + UUID.randomUUID()).documento("123456789")
                .endereco("Rua do teste").numero("10").bairro("Centro").cidade("Natal").estado("RN")
                .complemento("Sala 1").status(situacao).ativo(true)
                .enviadoEm(LocalDateTime.of(2026, 10, 1, 10, 0))
                .aprovadoEm(LocalDateTime.of(2026, 10, 2, 10, 0)).aprovadoPor(UUID.randomUUID().toString())
                .motivoRejeicao("Metadado preservado").fotoUrl("https://imagens.exemplo/foto.png").build());
    }

    private Agendamento criarAgendamento(Prestador prestador, StatusAgendamento situacao) {
        PerfilUsuario cliente = perfilUsuarioRepository.saveAndFlush(new PerfilUsuario(UUID.randomUUID()));
        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder().nome("Categoria " + UUID.randomUUID()).build());
        Servico servico = servicoRepository.saveAndFlush(Servico.builder().prestador(prestador).categoria(categoria)
                .nome("Corte").preco(new BigDecimal("40.00")).duracaoMin(30).fotoUrl("https://imagens.exemplo/corte.png").build());
        LocalDateTime inicio = LocalDateTime.of(2030, 1, 7, 9, 0);
        return agendamentoRepository.saveAndFlush(Agendamento.builder().cliente(cliente).prestador(prestador)
                .servico(servico).inicio(inicio).fim(inicio.plusMinutes(30)).status(situacao).build());
    }

    private Map<String, Object> cadastro(Long id) {
        return linha("prestadores", id);
    }

    private Map<String, Object> linha(String tabela, Object id) {
        return jdbcTemplate.queryForMap("SELECT * FROM " + tabela + " WHERE id = ?", id);
    }

    private Map<String, Object> semCamposDeInativacao(Map<String, Object> cadastro) {
        Map<String, Object> campos = new java.util.HashMap<>(cadastro);
        campos.remove("ativo");
        campos.remove("atualizado_em");
        return campos;
    }

    private String dono(Prestador prestador) {
        return prestador.getPerfilUsuario().getKeycloakId().toString();
    }

    private String rota(Prestador prestador) {
        return "/catalogo/prestadores/" + prestador.getId();
    }
}
