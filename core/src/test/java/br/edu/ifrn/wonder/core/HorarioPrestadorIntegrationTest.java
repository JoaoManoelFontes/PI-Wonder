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
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HorarioPrestadorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired private PrestadorRepository prestadorRepository;
    @Autowired private HorarioAtendimentoRepository horarioAtendimentoRepository;
    @Autowired private ServicoRepository servicoRepository;
    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDENTE")
    void deveCadastrarERemoverHorarioDoDonoSemAlterarPrestador(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(situacao);
        String corpo = mockMvc.perform(autenticada("POST", prestador, 0L)
                        .content("{\"dia_semana\":1,\"hora_inicio\":\"09:00\",\"hora_fim\":\"12:00\",\"prestador_id\":999999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$.dia_semana").value(1))
                .andExpect(jsonPath("$.hora_inicio").value("09:00:00"))
                .andExpect(jsonPath("$.hora_fim").value("12:00:00"))
                .andReturn().getResponse().getContentAsString();
        Long horarioId = ((Number) jsonMapper.readValue(corpo, Map.class).get("id")).longValue();
        horarioAtendimentoRepository.flush();
        entityManager.clear();
        HorarioAtendimento salvo = horarioAtendimentoRepository.findById(horarioId).orElseThrow();
        assertThat(salvo.getPrestador().getId()).isEqualTo(prestador.getId());
        assertThat(salvo.getDiaSemana()).isEqualTo(1);
        assertThat(salvo.getHoraInicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(salvo.getHoraFim()).isEqualTo(LocalTime.NOON);
        if (situacao == StatusPrestador.ATIVO) {
            mockMvc.perform(get("/catalogo/prestadores/{id}/horarios", prestador.getId()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(horarioId.intValue()));
        }

        mockMvc.perform(autenticada("DELETE", prestador, horarioId)).andExpect(status().isNoContent());
        horarioAtendimentoRepository.flush();
        entityManager.clear();
        assertThat(horarioAtendimentoRepository.findById(horarioId)).isEmpty();
        assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getStatus()).isEqualTo(situacao);
        mockMvc.perform(autenticada("DELETE", prestador, horarioId)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void deveAceitarTodosOsDiasDaSemanaEPrecisaoDeSegundos(int diaSemana) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        mockMvc.perform(autenticada("POST", prestador, 0L)
                        .content(jsonMapper.writeValueAsString(Map.of("dia_semana", diaSemana,
                                "hora_inicio", "00:00:00", "hora_fim", "00:00:01"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dia_semana").value(diaSemana))
                .andExpect(jsonPath("$.hora_inicio").value("00:00:00"))
                .andExpect(jsonPath("$.hora_fim").value("00:00:01"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 8, Integer.MAX_VALUE})
    void deveRejeitarDiaDaSemanaInvalido(int diaSemana) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        Map<String, Object> dados = dadosValidos();
        dados.put("dia_semana", diaSemana);
        mockMvc.perform(autenticada("POST", prestador, 0L).content(jsonMapper.writeValueAsString(dados)))
                .andExpect(status().isBadRequest());
        assertThat(horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestador.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dia_semana", "hora_inicio", "hora_fim"})
    void deveExigirTodosOsCampos(String campo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        Map<String, Object> dados = dadosValidos();
        dados.remove(campo);
        mockMvc.perform(autenticada("POST", prestador, 0L).content(jsonMapper.writeValueAsString(dados)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"12:00,12:00", "13:00,12:00", "23:00,01:00", "25:00,12:00", "09:60,12:00",
            "09:00,24:00", "09:00:00.000000001,12:00", "09:00,12:00:00.1", "texto,12:00"})
    void deveRejeitarHorasInvalidasOuIntervaloSemDuracaoPositiva(String inicio, String fim) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        mockMvc.perform(autenticada("POST", prestador, 0L).content(jsonMapper.writeValueAsString(
                        Map.of("dia_semana", 1, "hora_inicio", inicio, "hora_fim", fim))))
                .andExpect(status().isBadRequest());
        assertThat(horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestador.getId())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"09:00,12:00", "08:00,10:00", "11:00,13:00", "10:00,11:00", "08:00,13:00",
            "09:00,10:00", "11:00,12:00"})
    void deveBloquearSobreposicoesEDuplicatas(String inicio, String fim) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        criarHorario(prestador, 1, "09:00", "12:00");
        mockMvc.perform(autenticada("POST", prestador, 0L).content(jsonMapper.writeValueAsString(
                        Map.of("dia_semana", 1, "hora_inicio", inicio, "hora_fim", fim))))
                .andExpect(status().isConflict());
        assertThat(horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestador.getId())).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"08:00,09:00", "12:00,13:00", "14:00,15:00"})
    void devePermitirIntervalosAdjacentesOuSeparados(String inicio, String fim) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        criarHorario(prestador, 1, "09:00", "12:00");
        mockMvc.perform(autenticada("POST", prestador, 0L).content(jsonMapper.writeValueAsString(
                        Map.of("dia_semana", 1, "hora_inicio", inicio, "hora_fim", fim))))
                .andExpect(status().isCreated());
        assertThat(horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestador.getId())).hasSize(2);
    }

    @Test
    void horarioDeOutroDiaNaoDeveBloquearCadastro() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        criarHorario(prestador, 2, "09:00", "12:00");
        mockMvc.perform(autenticada("POST", prestador, 0L)).andExpect(status().isCreated());
    }

    @Test
    void horarioDeOutroPrestadorNaoDeveBloquearCadastro() throws Exception {
        criarHorario(criarPrestador(StatusPrestador.RASCUNHO), 1, "09:00", "12:00");
        mockMvc.perform(autenticada("POST", criarPrestador(StatusPrestador.RASCUNHO), 0L))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "DELETE"})
    void deveExigirLogin(String metodo) throws Exception {
        mockMvc.perform(requisicao(metodo, 1L, 1L)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "DELETE"})
    void deveBloquearOutroUsuarioMesmoComPapelAdmin(String metodo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        HorarioAtendimento horario = criarHorario(prestador, 1, "09:00", "12:00");
        mockMvc.perform(requisicao(metodo, prestador.getId(), horario.getId())
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        assertThat(horarioAtendimentoRepository.findById(horario.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "DELETE"})
    void deveBloquearAlteracoesDuranteAnalise(String metodo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        HorarioAtendimento horario = criarHorario(prestador, 1, "09:00", "12:00");
        mockMvc.perform(autenticada(metodo, prestador, horario.getId())).andExpect(status().isConflict());
        assertThat(horarioAtendimentoRepository.findById(horario.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "DELETE"})
    void deveRetornar404ParaPrestadorInexistente(String metodo) throws Exception {
        mockMvc.perform(requisicao(metodo, Long.MAX_VALUE, Long.MAX_VALUE)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar404ParaHorarioDeOutroPrestador() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        HorarioAtendimento outroHorario = criarHorario(criarPrestador(StatusPrestador.ATIVO), 1, "09:00", "12:00");
        mockMvc.perform(autenticada("DELETE", prestador, outroHorario.getId())).andExpect(status().isNotFound());
        assertThat(horarioAtendimentoRepository.findById(outroHorario.getId())).isPresent();
    }

    @Test
    void deveRetornar404ParaHorarioInexistente() throws Exception {
        mockMvc.perform(autenticada("DELETE", criarPrestador(StatusPrestador.ATIVO), Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void removerAtendimentoDeveAtualizarDisponibilidadeSemCancelarAgendamento() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        HorarioAtendimento horario = criarHorario(prestador, 1, "09:00", "12:00");
        Servico servico = servicoRepository.save(Servico.builder().prestador(prestador).nome("Servico agenda")
                .preco(new BigDecimal("30.00")).duracaoMin(30).status(StatusServico.ATIVO).build());
        PerfilUsuario cliente = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        Agendamento agendamento = agendamentoRepository.save(Agendamento.builder().cliente(cliente).prestador(prestador)
                .servico(servico).inicio(LocalDateTime.of(2099, 1, 5, 10, 0))
                .fim(LocalDateTime.of(2099, 1, 5, 10, 30)).status(StatusAgendamento.CONFIRMADO).build());

        mockMvc.perform(get("/agendamentos/disponibilidade").param("prestador_id", prestador.getId().toString())
                        .param("servico_id", servico.getId().toString()).param("data", "2099-01-05"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.horarios.length()").value(5));
        mockMvc.perform(autenticada("DELETE", prestador, horario.getId())).andExpect(status().isNoContent());
        horarioAtendimentoRepository.flush();
        mockMvc.perform(get("/agendamentos/disponibilidade").param("prestador_id", prestador.getId().toString())
                        .param("servico_id", servico.getId().toString()).param("data", "2099-01-05"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.horarios").isEmpty());
        entityManager.clear();
        Agendamento salvo = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(salvo.getInicio()).isEqualTo(LocalDateTime.of(2099, 1, 5, 10, 0));
        assertThat(salvo.getFim()).isEqualTo(LocalDateTime.of(2099, 1, 5, 10, 30));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deveImpedirSobreposicaoEmDuasRequisicoesSimultaneas() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        Long prestadorId = prestador.getId();
        UUID perfilId = prestador.getPerfilUsuario().getId();
        CountDownLatch prontos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> enviar = () -> {
                prontos.countDown();
                if (!largada.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Tempo limite aguardando a largada");
                }
                return mockMvc.perform(autenticada("POST", prestador, 0L)).andReturn().getResponse().getStatus();
            };
            var primeira = executor.submit(enviar);
            var segunda = executor.submit(enviar);
            boolean iniciados = prontos.await(10, TimeUnit.SECONDS);
            largada.countDown();
            assertThat(iniciados).isTrue();
            assertThat(List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestadorId)).hasSize(1);
        } finally {
            horarioAtendimentoRepository.deleteAll(
                    horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestadorId));
            prestadorRepository.deleteById(prestadorId);
            perfilUsuarioRepository.deleteById(perfilId);
        }
    }

    private MockHttpServletRequestBuilder requisicao(String metodo, Long prestadorId, Long horarioId) {
        String caminho = "/catalogo/prestadores/" + prestadorId + "/horarios";
        if (metodo.equals("DELETE")) {
            caminho += "/" + horarioId;
        }
        return request(HttpMethod.valueOf(metodo), caminho).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dia_semana\":1,\"hora_inicio\":\"09:00\",\"hora_fim\":\"12:00\"}");
    }

    private MockHttpServletRequestBuilder autenticada(String metodo, Prestador prestador, Long horarioId) {
        return requisicao(metodo, prestador.getId(), horarioId)
                .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString())));
    }

    private Map<String, Object> dadosValidos() {
        return new HashMap<>(Map.of("dia_semana", 1, "hora_inicio", "09:00", "hora_fim", "12:00"));
    }

    private Prestador criarPrestador(StatusPrestador situacao) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder().perfilUsuario(perfil).nomeEstab("Studio horarios proprios")
                .status(situacao).ativo(situacao == StatusPrestador.ATIVO).build());
    }

    private HorarioAtendimento criarHorario(Prestador prestador, int diaSemana, String inicio, String fim) {
        return horarioAtendimentoRepository.save(HorarioAtendimento.builder().prestador(prestador).diaSemana(diaSemana)
                .horaInicio(LocalTime.parse(inicio)).horaFim(LocalTime.parse(fim)).build());
    }
}
