package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HistoricoAgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgendamentoStatusIntegrationTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2099, 1, 5, 9, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private HistoricoAgendamentoRepository historicoRepository;
    @Autowired private PerfilUsuarioRepository perfilRepository;
    @Autowired private PrestadorRepository prestadorRepository;
    @Autowired private ServicoRepository servicoRepository;
    @Autowired private HorarioAtendimentoRepository horarioRepository;

    @ParameterizedTest
    @CsvSource({
            "PENDENTE,confirmado,PROVIDER", "PENDENTE,confirmado,ADMIN",
            "CONFIRMADO,concluido,PROVIDER", "CONFIRMADO,concluido,ADMIN",
            "PENDENTE,cancelado,CUSTOMER", "PENDENTE,cancelado,PROVIDER", "PENDENTE,cancelado,ADMIN",
            "CONFIRMADO,cancelado,CUSTOMER", "CONFIRMADO,cancelado,PROVIDER", "CONFIRMADO,cancelado,ADMIN"
    })
    void deveAplicarTransicoesPermitidasERegistrarHistorico(StatusAgendamento anterior, String novo, String papel)
            throws Exception {
        Agendamento agendamento = criarAgendamento(anterior, INICIO);
        UUID usuarioId = usuario(agendamento, papel);
        mockMvc.perform(autenticada(agendamento, novo, "  Mudança de planos  ", usuarioId, papel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agendamento.getId().intValue()))
                .andExpect(jsonPath("$.cliente_id").value(agendamento.getCliente().getId().toString()))
                .andExpect(jsonPath("$.status").value(novo));

        assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getStatus().name())
                .isEqualTo(novo.toUpperCase(java.util.Locale.ROOT));
        assertThat(historicoRepository.findByAgendamentoIdOrderByIdAsc(agendamento.getId()))
                .singleElement().satisfies(historico -> {
                    assertThat(historico.getStatusAnterior()).isEqualTo(anterior);
                    assertThat(historico.getStatusNovo().name()).isEqualTo(novo.toUpperCase(java.util.Locale.ROOT));
                    assertThat(historico.getUsuarioKeycloakId()).isEqualTo(usuarioId);
                    assertThat(historico.getMotivo()).isEqualTo("Mudança de planos");
                    assertThat(historico.getAlteradoEm()).isNotNull();
                });
    }

    @ParameterizedTest
    @CsvSource({
            "PENDENTE,pendente", "PENDENTE,concluido",
            "CONFIRMADO,pendente", "CONFIRMADO,confirmado",
            "CANCELADO,pendente", "CANCELADO,confirmado", "CANCELADO,cancelado", "CANCELADO,concluido",
            "CONCLUIDO,pendente", "CONCLUIDO,confirmado", "CONCLUIDO,cancelado", "CONCLUIDO,concluido"
    })
    void deveRejeitarTransicaoInvalidaSemHistorico(StatusAgendamento anterior, String novo) throws Exception {
        Agendamento agendamento = criarAgendamento(anterior, INICIO);
        mockMvc.perform(autenticada(agendamento, novo, "Motivo", usuario(agendamento, "PROVIDER"), "PROVIDER"))
                .andExpect(status().isConflict());
        verificarSemAlteracao(agendamento, anterior);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirmado", "concluido"})
    void clienteNaoPodeConfirmarOuConcluir(String novo) throws Exception {
        Agendamento agendamento = criarAgendamento(StatusAgendamento.CONFIRMADO, INICIO);
        mockMvc.perform(autenticada(agendamento, novo, null, usuario(agendamento, "CUSTOMER"), "CUSTOMER"))
                .andExpect(status().isForbidden());
        verificarSemAlteracao(agendamento, StatusAgendamento.CONFIRMADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "PROVIDER"})
    void deveBloquearOutroUsuarioMesmoComPapelDePrestador(String papel) throws Exception {
        Agendamento agendamento = criarAgendamento(StatusAgendamento.PENDENTE, INICIO);
        mockMvc.perform(autenticada(agendamento, "cancelado", "Motivo", UUID.randomUUID(), papel))
                .andExpect(status().isForbidden());
        verificarSemAlteracao(agendamento, StatusAgendamento.PENDENTE);
    }

    @Test
    void donoDoPrestadorSemPapelProviderNaoPodeConfirmar() throws Exception {
        Agendamento agendamento = criarAgendamento(StatusAgendamento.PENDENTE, INICIO);
        mockMvc.perform(autenticada(agendamento, "confirmado", null, usuario(agendamento, "PROVIDER"), "CUSTOMER"))
                .andExpect(status().isForbidden());
        verificarSemAlteracao(agendamento, StatusAgendamento.PENDENTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\" \"}", "{\"status\":\"inexistente\"}",
            "{\"status\":\"cancelado\"}", "{\"status\":\"cancelado\",\"motivo\":\"  \"}"})
    void deveValidarCorpoEMotivoSemAlterarAgendamento(String corpo) throws Exception {
        Agendamento agendamento = criarAgendamento(StatusAgendamento.PENDENTE, INICIO);
        mockMvc.perform(requisicao(agendamento.getId()).content(corpo)
                        .with(jwt().jwt(token -> token.subject(usuario(agendamento, "CUSTOMER").toString()))))
                .andExpect(status().isBadRequest());
        verificarSemAlteracao(agendamento, StatusAgendamento.PENDENTE);
    }

    @Test
    void deveRejeitarMotivoAcimaDoLimite() throws Exception {
        Agendamento agendamento = criarAgendamento(StatusAgendamento.PENDENTE, INICIO);
        mockMvc.perform(autenticada(agendamento, "cancelado", "a".repeat(1001),
                        usuario(agendamento, "CUSTOMER"), "CUSTOMER"))
                .andExpect(status().isBadRequest());
        verificarSemAlteracao(agendamento, StatusAgendamento.PENDENTE);
    }

    @ParameterizedTest
    @ValueSource(ints = {-30, 0, 14})
    void deveBloquearCancelamentoSemAntecedencia(int minutos) throws Exception {
        Agendamento agendamento = criarAgendamento(StatusAgendamento.PENDENTE, LocalDateTime.now().plusMinutes(minutos));
        mockMvc.perform(autenticada(agendamento, "cancelado", "Motivo", usuario(agendamento, "CUSTOMER"), "CUSTOMER"))
                .andExpect(status().isConflict());
        verificarSemAlteracao(agendamento, StatusAgendamento.PENDENTE);
    }

    @Test
    void deveExigirJwtValidoERetornar404ParaAgendamentoInexistente() throws Exception {
        mockMvc.perform(requisicao(Long.MAX_VALUE).content("{\"status\":\"confirmado\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(requisicao(Long.MAX_VALUE).with(jwt().jwt(token -> token.subject("invalido")))
                        .content("{\"status\":\"confirmado\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(requisicao(Long.MAX_VALUE).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
                        .content("{\"status\":\"confirmado\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelamentoDeveLiberarHorarioNaConsultaPublica() throws Exception {
        Agendamento agendamento = criarAgendamento(StatusAgendamento.PENDENTE, INICIO);
        horarioRepository.saveAndFlush(HorarioAtendimento.builder().prestador(agendamento.getPrestador())
                .diaSemana(1).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(12, 0)).build());
        verificarQuantidadeDeHorarios(agendamento, 2);
        mockMvc.perform(autenticada(agendamento, "cancelado", "Não poderei comparecer",
                        usuario(agendamento, "CUSTOMER"), "CUSTOMER"))
                .andExpect(status().isOk());
        verificarQuantidadeDeHorarios(agendamento, 3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancelamentosSimultaneosDevemGerarUmaUnicaAlteracao() throws Exception {
        Agendamento agendamento = criarAgendamento(StatusAgendamento.PENDENTE, INICIO);
        CountDownLatch prontos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> enviar = () -> {
                prontos.countDown();
                if (!largada.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Tempo limite aguardando largada");
                }
                return mockMvc.perform(autenticada(agendamento, "cancelado", "Motivo",
                                usuario(agendamento, "CUSTOMER"), "CUSTOMER"))
                        .andReturn().getResponse().getStatus();
            };
            var primeira = executor.submit(enviar);
            var segunda = executor.submit(enviar);
            boolean iniciados = prontos.await(10, TimeUnit.SECONDS);
            largada.countDown();
            assertThat(iniciados).isTrue();
            assertThat(List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getStatus())
                    .isEqualTo(StatusAgendamento.CANCELADO);
            assertThat(historicoRepository.findByAgendamentoIdOrderByIdAsc(agendamento.getId())).hasSize(1);
        } finally {
            historicoRepository.deleteAll(historicoRepository.findByAgendamentoIdOrderByIdAsc(agendamento.getId()));
            agendamentoRepository.deleteById(agendamento.getId());
            servicoRepository.deleteById(agendamento.getServico().getId());
            prestadorRepository.deleteById(agendamento.getPrestador().getId());
            perfilRepository.deleteById(agendamento.getCliente().getId());
            perfilRepository.deleteById(agendamento.getPrestador().getPerfilUsuario().getId());
        }
    }

    private void verificarQuantidadeDeHorarios(Agendamento agendamento, int quantidade) throws Exception {
        mockMvc.perform(get("/agendamentos/disponibilidade")
                        .param("prestador_id", agendamento.getPrestador().getId().toString())
                        .param("servico_id", agendamento.getServico().getId().toString()).param("data", "2099-01-05"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.horarios.length()").value(quantidade));
    }

    private void verificarSemAlteracao(Agendamento agendamento, StatusAgendamento anterior) {
        assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getStatus()).isEqualTo(anterior);
        assertThat(historicoRepository.findByAgendamentoIdOrderByIdAsc(agendamento.getId())).isEmpty();
    }

    private MockHttpServletRequestBuilder autenticada(Agendamento agendamento, String novo, String motivo,
            UUID usuarioId, String papel) {
        String campoMotivo = motivo == null ? "null" : "\"" + motivo + "\"";
        return requisicao(agendamento.getId()).content("{\"status\":\"%s\",\"motivo\":%s}".formatted(novo, campoMotivo))
                .with(jwt().jwt(token -> token.subject(usuarioId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + papel)));
    }

    private MockHttpServletRequestBuilder requisicao(Long id) {
        return patch("/agendamentos/{id}/status", id).contentType(MediaType.APPLICATION_JSON);
    }

    private UUID usuario(Agendamento agendamento, String papel) {
        return switch (papel) {
            case "CUSTOMER" -> agendamento.getCliente().getKeycloakId();
            case "PROVIDER" -> agendamento.getPrestador().getPerfilUsuario().getKeycloakId();
            default -> UUID.randomUUID();
        };
    }

    private Agendamento criarAgendamento(StatusAgendamento situacao, LocalDateTime inicio) {
        PerfilUsuario cliente = perfilRepository.saveAndFlush(new PerfilUsuario(UUID.randomUUID()));
        PerfilUsuario dono = perfilRepository.saveAndFlush(new PerfilUsuario(UUID.randomUUID()));
        Prestador prestador = prestadorRepository.saveAndFlush(Prestador.builder().perfilUsuario(dono)
                .nomeEstab("Studio status").ativo(true).status(StatusPrestador.ATIVO).build());
        Servico servico = servicoRepository.saveAndFlush(Servico.builder().prestador(prestador).nome("Corte")
                .preco(new BigDecimal("50.00")).duracaoMin(60).build());
        return agendamentoRepository.saveAndFlush(Agendamento.builder().cliente(cliente).prestador(prestador)
                .servico(servico).inicio(inicio).fim(inicio.plusHours(1)).status(situacao).build());
    }
}
