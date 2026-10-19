package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.dto.DisponibilidadeResponse;
import br.edu.ifrn.wonder.core.dto.DiasDisponiveisResponse;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DisponibilidadePublicaIntegrationTest {

    private static final LocalDate SEGUNDA = LocalDate.of(2099, 1, 5);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private HorarioAtendimentoRepository horarioAtendimentoRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @ParameterizedTest
    @ValueSource(strings = {"dias-disponiveis", "disponibilidade"})
    void deveRejeitarParametrosAusentesOuInvalidosSemLogin(String rota) throws Exception {
        String periodo = rota.equals("dias-disponiveis") ? "mes" : "data";
        Map<String, String> parametros = Map.of(
                "prestador_id", "1", "servico_id", "1",
                periodo, periodo.equals("mes") ? "2099-01" : "2099-01-05");

        for (String campo : parametros.keySet()) {
            MockHttpServletRequestBuilder requisicao = get("/agendamentos/" + rota);
            parametros.forEach((nome, valor) -> {
                if (!nome.equals(campo)) {
                    requisicao.param(nome, valor);
                }
            });
            mockMvc.perform(requisicao).andExpect(status().isBadRequest());

            MockHttpServletRequestBuilder invalida = get("/agendamentos/" + rota);
            parametros.forEach((nome, valor) -> invalida.param(nome, nome.equals(campo) ? "invalido" : valor));
            mockMvc.perform(invalida).andExpect(status().isBadRequest());
        }

        mockMvc.perform(get("/agendamentos/" + rota)
                        .param("prestador_id", "1").param("servico_id", "1")
                        .param(periodo, periodo.equals("mes") ? "2099-13" : "2099-02-30"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dias-disponiveis", "disponibilidade"})
    void deveRetornar404ParaServicoInexistenteInativoOuDeOutroPrestador(String rota) throws Exception {
        Prestador prestador = criarPrestador();
        Servico servico = criarServico(prestador, 60);
        Servico outroServico = criarServico(criarPrestador(), 60);

        mockMvc.perform(consulta(rota, Long.MAX_VALUE, servico.getId())).andExpect(status().isNotFound());
        mockMvc.perform(consulta(rota, prestador.getId(), Long.MAX_VALUE)).andExpect(status().isNotFound());
        mockMvc.perform(consulta(rota, prestador.getId(), outroServico.getId())).andExpect(status().isNotFound());

        servico.setStatus(StatusServico.INATIVO);
        servicoRepository.flush();
        mockMvc.perform(consulta(rota, prestador.getId(), servico.getId())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dias-disponiveis", "disponibilidade"})
    void deveRetornar404ParaPrestadorNaoPublicavel(String rota) throws Exception {
        Prestador prestador = criarPrestador();
        Servico servico = criarServico(prestador, 60);
        for (StatusPrestador situacao : StatusPrestador.values()) {
            for (boolean ativo : new boolean[] {false, true}) {
                if (situacao == StatusPrestador.ATIVO && ativo) {
                    continue;
                }
                prestador.setStatus(situacao);
                prestador.setAtivo(ativo);
                prestadorRepository.flush();
                mockMvc.perform(consulta(rota, prestador.getId(), servico.getId()))
                        .andExpect(status().isNotFound());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(StatusAgendamento.class)
    void deveBloquearConflitosDeTodosOsServicosExcetoCancelados(StatusAgendamento situacao) throws Exception {
        Prestador prestador = criarPrestador();
        Servico servico = criarServico(prestador, 60);
        criarHorario(prestador, "09:00", "12:00");
        criarAgendamento(criarServico(prestador, 30), SEGUNDA.atTime(10, 0), SEGUNDA.atTime(11, 0), situacao);

        DisponibilidadeResponse resposta = consultarDia(prestador, servico, SEGUNDA);

        if (situacao == StatusAgendamento.CANCELADO) {
            assertThat(resposta.horarios()).extracting(horario -> horario.inicio().toLocalTime())
                    .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
        } else {
            assertThat(resposta.horarios()).extracting(horario -> horario.inicio().toLocalTime())
                    .containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0));
        }
    }

    @Test
    void deveBloquearSobreposicaoParcialEPreservarLimitesSemConflito() throws Exception {
        Prestador prestador = criarPrestador();
        Servico servico = criarServico(prestador, 60);
        criarHorario(prestador, "09:00", "12:00");
        criarAgendamento(servico, SEGUNDA.atTime(9, 30), SEGUNDA.atTime(10, 30), StatusAgendamento.CONFIRMADO);
        criarAgendamento(servico, SEGUNDA.atTime(8, 0), SEGUNDA.atTime(9, 0), StatusAgendamento.CONFIRMADO);
        criarAgendamento(servico, SEGUNDA.atTime(12, 0), SEGUNDA.atTime(13, 0), StatusAgendamento.CONFIRMADO);
        criarAgendamento(criarServico(criarPrestador(), 60), SEGUNDA.atTime(11, 0),
                SEGUNDA.atTime(12, 0), StatusAgendamento.CONFIRMADO);

        DisponibilidadeResponse resposta = consultarDia(prestador, servico, SEGUNDA);

        assertThat(resposta.horarios()).hasSize(1);
        assertThat(resposta.horarios().getFirst().inicio()).isEqualTo(SEGUNDA.atTime(11, 0));
        assertThat(resposta.horarios().getFirst().fim()).isEqualTo(SEGUNDA.atTime(12, 0));
    }

    @ParameterizedTest
    @CsvSource({"30, 4", "45, 2", "60, 2", "150, 0"})
    void deveRespeitarDuracaoPausasEFimDoAtendimento(int duracao, int quantidade) throws Exception {
        Prestador prestador = criarPrestador();
        Servico servico = criarServico(prestador, duracao);
        criarHorario(prestador, "09:00", "10:00");
        criarHorario(prestador, "11:00", "12:00");

        DisponibilidadeResponse resposta = consultarDia(prestador, servico, SEGUNDA);

        assertThat(resposta.duracaoMin()).isEqualTo(duracao);
        assertThat(resposta.horarios()).hasSize(quantidade).allSatisfy(horario -> {
            assertThat(horario.fim()).isEqualTo(horario.inicio().plusMinutes(duracao));
            assertThat(horario.inicio().toLocalDate()).isEqualTo(SEGUNDA);
            assertThat(horario.inicio().toLocalTime()).isAfterOrEqualTo(LocalTime.of(9, 0));
            assertThat(horario.fim().toLocalTime()).isBeforeOrEqualTo(LocalTime.NOON);
            assertThat(!horario.fim().toLocalTime().isAfter(LocalTime.of(10, 0))
                    || !horario.inicio().toLocalTime().isBefore(LocalTime.of(11, 0))).isTrue();
        });
    }

    @Test
    void deveManterDiasDoMesConsistentesComHorariosLivresDeCadaDia() throws Exception {
        Prestador prestador = criarPrestador();
        Servico servico = criarServico(prestador, 60);
        criarHorario(prestador, "09:00", "12:00");
        criarAgendamento(servico, SEGUNDA.atTime(9, 0), SEGUNDA.atTime(12, 0), StatusAgendamento.CONFIRMADO);
        criarAgendamento(servico, SEGUNDA.plusWeeks(1).atTime(9, 0),
                SEGUNDA.plusWeeks(1).atTime(12, 0), StatusAgendamento.CANCELADO);

        String corpo = mockMvc.perform(consulta("dias-disponiveis", prestador.getId(), servico.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        DiasDisponiveisResponse resposta = jsonMapper.readValue(corpo, DiasDisponiveisResponse.class);

        assertThat(resposta.prestadorId()).isEqualTo(prestador.getId());
        assertThat(resposta.servicoId()).isEqualTo(servico.getId());
        assertThat(resposta.mes()).isEqualTo("2099-01");
        assertThat(resposta.dias()).allSatisfy(dia -> assertThat(dia.disponivel()).isTrue())
                .extracting(dia -> dia.data())
                .containsExactly(SEGUNDA.plusWeeks(1), SEGUNDA.plusWeeks(2), SEGUNDA.plusWeeks(3));

        for (LocalDate data = SEGUNDA.withDayOfMonth(1); data.getMonthValue() == 1; data = data.plusDays(1)) {
            DisponibilidadeResponse dia = consultarDia(prestador, servico, data);
            LocalDate dataConsultada = data;
            assertThat(resposta.dias().stream().anyMatch(item -> item.data().equals(dataConsultada)))
                    .as("Dia %s deve ter a mesma disponibilidade nas duas consultas", data)
                    .isEqualTo(!dia.horarios().isEmpty());
        }
    }

    @Test
    void deveConsiderarAgendamentoQueComecaNoMesAnterior() throws Exception {
        Prestador prestador = criarPrestador();
        Servico servico = criarServico(prestador, 60);
        LocalDate primeiroDia = LocalDate.of(2099, 1, 1);
        horarioAtendimentoRepository.save(HorarioAtendimento.builder().prestador(prestador)
                .diaSemana(primeiroDia.getDayOfWeek().getValue())
                .horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(10, 0)).build());
        criarAgendamento(servico, primeiroDia.minusDays(1).atTime(23, 30),
                primeiroDia.atTime(9, 30), StatusAgendamento.CONFIRMADO);

        assertThat(consultarDia(prestador, servico, primeiroDia).horarios()).isEmpty();
        String corpo = mockMvc.perform(consulta("dias-disponiveis", prestador.getId(), servico.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        DiasDisponiveisResponse resposta = jsonMapper.readValue(corpo, DiasDisponiveisResponse.class);
        assertThat(resposta.dias()).extracting(dia -> dia.data())
                .containsExactly(primeiroDia.plusWeeks(1), primeiroDia.plusWeeks(2),
                        primeiroDia.plusWeeks(3), primeiroDia.plusWeeks(4));
    }

    private MockHttpServletRequestBuilder consulta(String rota, Long prestadorId, Long servicoId) {
        MockHttpServletRequestBuilder requisicao = get("/agendamentos/" + rota)
                .param("prestador_id", prestadorId.toString()).param("servico_id", servicoId.toString());
        return rota.equals("dias-disponiveis") ? requisicao.param("mes", "2099-01")
                : requisicao.param("data", SEGUNDA.toString());
    }

    private DisponibilidadeResponse consultarDia(Prestador prestador, Servico servico, LocalDate data) throws Exception {
        String corpo = mockMvc.perform(get("/agendamentos/disponibilidade")
                        .param("prestador_id", prestador.getId().toString())
                        .param("servico_id", servico.getId().toString()).param("data", data.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readValue(corpo, DisponibilidadeResponse.class);
    }

    private Prestador criarPrestador() {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder().perfilUsuario(perfil)
                .nomeEstab("Studio revisao de disponibilidade").status(StatusPrestador.ATIVO).ativo(true).build());
    }

    private Servico criarServico(Prestador prestador, int duracao) {
        return servicoRepository.save(Servico.builder().prestador(prestador).nome("Servico de revisao")
                .preco(new BigDecimal("50.00")).duracaoMin(duracao).status(StatusServico.ATIVO).build());
    }

    private void criarHorario(Prestador prestador, String inicio, String fim) {
        horarioAtendimentoRepository.save(HorarioAtendimento.builder().prestador(prestador).diaSemana(1)
                .horaInicio(LocalTime.parse(inicio)).horaFim(LocalTime.parse(fim)).build());
    }

    private void criarAgendamento(Servico servico, LocalDateTime inicio, LocalDateTime fim, StatusAgendamento situacao) {
        PerfilUsuario cliente = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        agendamentoRepository.save(Agendamento.builder().cliente(cliente).prestador(servico.getPrestador())
                .servico(servico).inicio(inicio).fim(fim).status(situacao).build());
    }
}
