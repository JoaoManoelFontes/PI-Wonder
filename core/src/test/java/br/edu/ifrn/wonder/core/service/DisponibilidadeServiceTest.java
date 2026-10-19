package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.dto.DisponibilidadeResponse;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;

@ExtendWith(MockitoExtension.class)
class DisponibilidadeServiceTest {

    private static final Long PRESTADOR_ID = 1L;
    private static final Long SERVICO_ID = 2L;
    private static final LocalDate SEGUNDA = LocalDate.of(2099, 1, 5);

    @Mock
    private CatalogoPublicoService catalogoPublicoService;

    @Mock
    private HorarioAtendimentoRepository horarioAtendimentoRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @InjectMocks
    private DisponibilidadeService disponibilidadeService;

    @BeforeEach
    void prepararCatalogoEAgenda() {
        when(catalogoPublicoService.buscarServicoAtivo(PRESTADOR_ID, SERVICO_ID))
                .thenReturn(Servico.builder().id(SERVICO_ID).duracaoMin(60).build());
        when(horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(PRESTADOR_ID))
                .thenReturn(List.of(horario(1, "09:00", "12:00")));
    }

    @Test
    void deveCalcularHorariosLivresDentroDaAgendaDoPrestador() {
        semAgendamentosNoDia();

        DisponibilidadeResponse resposta = disponibilidadeService.calcular(PRESTADOR_ID, SERVICO_ID, SEGUNDA);

        assertThat(resposta.prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(resposta.servicoId()).isEqualTo(SERVICO_ID);
        assertThat(resposta.data()).isEqualTo(SEGUNDA);
        assertThat(resposta.duracaoMin()).isEqualTo(60);
        assertThat(resposta.horarios())
                .extracting(horario -> horario.inicio().toLocalTime())
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
    }

    @Test
    void deveRemoverHorarioQueConflitaComAgendamentoExistente() {
        when(agendamentoRepository
                .findByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusNotOrderByInicioAsc(
                        PRESTADOR_ID,
                        SEGUNDA.plusDays(1).atStartOfDay(),
                        SEGUNDA.atStartOfDay(),
                        StatusAgendamento.CANCELADO))
                .thenReturn(List.of(agendamento("10:00", "11:00", StatusAgendamento.CONFIRMADO)));

        DisponibilidadeResponse resposta = disponibilidadeService.calcular(PRESTADOR_ID, SERVICO_ID, SEGUNDA);

        assertThat(resposta.horarios())
                .extracting(horario -> horario.inicio().toLocalTime())
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0));
    }

    @Test
    void deveManterHorarioLivreQuandoAgendamentoEstaCancelado() {
        when(agendamentoRepository
                .findByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusNotOrderByInicioAsc(
                        PRESTADOR_ID,
                        SEGUNDA.plusDays(1).atStartOfDay(),
                        SEGUNDA.atStartOfDay(),
                        StatusAgendamento.CANCELADO))
                .thenReturn(List.of(agendamento("10:00", "11:00", StatusAgendamento.CANCELADO)));

        DisponibilidadeResponse resposta = disponibilidadeService.calcular(PRESTADOR_ID, SERVICO_ID, SEGUNDA);

        assertThat(resposta.horarios()).hasSize(3);
    }

    @Test
    void deveRetornarListaVaziaParaDiaForaDaAgendaDoPrestador() {
        semAgendamentosNoDia();

        DisponibilidadeResponse resposta = disponibilidadeService.calcular(
                PRESTADOR_ID,
                SERVICO_ID,
                SEGUNDA.plusDays(1));

        assertThat(resposta.horarios()).isEmpty();
    }

    @Test
    void deveGerarIntervalosDeTrintaMinutos() {
        configurarDuracaoDoServico(30);
        configurarAgenda("09:00", "11:00");
        semAgendamentosNoDia();

        DisponibilidadeResponse resposta = disponibilidadeService.calcular(PRESTADOR_ID, SERVICO_ID, SEGUNDA);

        assertThat(resposta.duracaoMin()).isEqualTo(30);
        assertThat(resposta.horarios())
                .extracting(horario -> horario.inicio().toLocalTime())
                .containsExactly(
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 30),
                        LocalTime.of(10, 0),
                        LocalTime.of(10, 30));
    }

    @Test
    void deveGerarIntervalosDeQuarentaECincoMinutos() {
        configurarDuracaoDoServico(45);
        configurarAgenda("09:00", "12:00");
        semAgendamentosNoDia();

        DisponibilidadeResponse resposta = disponibilidadeService.calcular(PRESTADOR_ID, SERVICO_ID, SEGUNDA);

        assertThat(resposta.duracaoMin()).isEqualTo(45);
        assertThat(resposta.horarios())
                .extracting(horario -> horario.inicio().toLocalTime())
                .containsExactly(
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 45),
                        LocalTime.of(10, 30),
                        LocalTime.of(11, 15));
    }

    @Test
    void naoDeveRetornarIntervaloIncompletoNoFimDaAgenda() {
        configurarDuracaoDoServico(50);
        configurarAgenda("09:00", "12:00");
        semAgendamentosNoDia();

        DisponibilidadeResponse resposta = disponibilidadeService.calcular(PRESTADOR_ID, SERVICO_ID, SEGUNDA);

        assertThat(resposta.horarios())
                .extracting(horario -> horario.inicio().toLocalTime())
                .containsExactly(
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 50),
                        LocalTime.of(10, 40));
        assertThat(resposta.horarios())
                .allSatisfy(horario -> assertThat(horario.fim().toLocalTime())
                        .isBeforeOrEqualTo(LocalTime.NOON));
    }

    private void semAgendamentosNoDia() {
        when(agendamentoRepository
                .findByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusNotOrderByInicioAsc(
                        eq(PRESTADOR_ID),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(StatusAgendamento.CANCELADO)))
                .thenReturn(List.of());
    }

    private void configurarDuracaoDoServico(int duracaoMin) {
        when(catalogoPublicoService.buscarServicoAtivo(PRESTADOR_ID, SERVICO_ID))
                .thenReturn(Servico.builder().id(SERVICO_ID).duracaoMin(duracaoMin).build());
    }

    private void configurarAgenda(String inicio, String fim) {
        when(horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(PRESTADOR_ID))
                .thenReturn(List.of(horario(1, inicio, fim)));
    }

    private HorarioAtendimento horario(int diaSemana, String inicio, String fim) {
        return HorarioAtendimento.builder()
                .diaSemana(diaSemana)
                .horaInicio(LocalTime.parse(inicio))
                .horaFim(LocalTime.parse(fim))
                .build();
    }

    private Agendamento agendamento(String inicio, String fim, StatusAgendamento status) {
        return Agendamento.builder()
                .inicio(LocalDateTime.of(SEGUNDA, LocalTime.parse(inicio)))
                .fim(LocalDateTime.of(SEGUNDA, LocalTime.parse(fim)))
                .status(status)
                .build();
    }
}
