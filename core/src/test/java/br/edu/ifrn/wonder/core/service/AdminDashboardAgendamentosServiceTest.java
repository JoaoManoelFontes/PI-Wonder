package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.dto.AgendamentosDiaResponse;
import br.edu.ifrn.wonder.core.dto.AgendamentosTotaisStatusResponse;
import br.edu.ifrn.wonder.core.repository.AgendamentoContagemDiaria;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@ExtendWith(MockitoExtension.class)
class AdminDashboardAgendamentosServiceTest {

    @Mock
    private KeycloakAdminService keycloakAdminService;
    @Mock
    private PrestadorRepository prestadorRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Test
    void deveReconciliarResumoEStatusAPartirDaMesmaConsultaEPreencherDiasVazios() {
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        var contagens = List.of(
                contagem("2026-11-03", "CONCLUIDO", 2),
                contagem("2026-11-01", "CONFIRMADO", 1),
                contagem("2026-11-01", "CANCELADO", 1),
                contagem("2026-11-01", "PENDENTE", 1));
        when(agendamentoRepository.contarPorDiaEStatus(inicio.atStartOfDay(), inicio.plusDays(3).atStartOfDay()))
                .thenReturn(contagens);

        var resposta = servico("2026-11-13T12:00:00Z").consultarAgendamentos(inicio, inicio.plusDays(2));

        assertThat(resposta.dataInicio()).isEqualTo(inicio);
        assertThat(resposta.dataFim()).isEqualTo(inicio.plusDays(2));
        assertThat(resposta.totalAgendamentos()).isEqualTo(5);
        assertThat(resposta.mediaPorDia()).isEqualByComparingTo("1.67");
        assertThat(resposta.totaisPorStatus()).isEqualTo(new AgendamentosTotaisStatusResponse(1, 1, 1, 2));
        assertThat(resposta.dias()).extracting(AgendamentosDiaResponse::data)
                .containsExactly(inicio, inicio.plusDays(1), inicio.plusDays(2));
        assertThat(resposta.dias()).extracting(AgendamentosDiaResponse::totalAgendamentos)
                .containsExactly(3L, 0L, 2L);
        assertThat(resposta.dias().get(1).totaisPorStatus())
                .isEqualTo(new AgendamentosTotaisStatusResponse(0, 0, 0, 0));
        assertThat(resposta.dias().get(2).totaisPorStatus())
                .isEqualTo(new AgendamentosTotaisStatusResponse(0, 0, 0, 2));
        assertThat(resposta.dias().stream().mapToLong(AgendamentosDiaResponse::totalAgendamentos).sum())
                .isEqualTo(resposta.totalAgendamentos());
        verify(agendamentoRepository).contarPorDiaEStatus(inicio.atStartOfDay(), inicio.plusDays(3).atStartOfDay());
        verifyNoMoreInteractions(agendamentoRepository);
        verifyNoInteractions(keycloakAdminService, prestadorRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-12-01T01:00:00Z, 2026-11-01, 2026-11-30, 30",
            "2026-12-15T12:00:00Z, 2026-12-01, 2026-12-31, 31",
            "2027-01-01T03:00:00Z, 2027-01-01, 2027-01-31, 31",
            "2028-02-29T12:00:00Z, 2028-02-01, 2028-02-29, 29"
    })
    void deveUsarMesCompletoDeSaoPauloEPreencherPeriodoVazio(
            String instante, LocalDate inicio, LocalDate fim, int quantidadeDias) {
        var resposta = servico(instante).consultarAgendamentos(null, null);

        assertThat(resposta.dataInicio()).isEqualTo(inicio);
        assertThat(resposta.dataFim()).isEqualTo(fim);
        assertThat(resposta.totalAgendamentos()).isZero();
        assertThat(resposta.mediaPorDia()).isEqualByComparingTo("0.00");
        assertThat(resposta.dias()).hasSize(quantidadeDias).allSatisfy(dia -> {
            assertThat(dia.totalAgendamentos()).isZero();
            assertThat(dia.totaisPorStatus()).isEqualTo(new AgendamentosTotaisStatusResponse(0, 0, 0, 0));
        });
        verify(agendamentoRepository).contarPorDiaEStatus(inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay());
        verifyNoInteractions(keycloakAdminService, prestadorRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-13, 2026-11-13, 1, 2, 2.00",
            "2026-11-01, 2026-11-03, 3, 2, 0.67",
            "2026-11-01, 2026-11-08, 8, 1, 0.13",
            "2026-11-01, 2026-11-30, 30, 15, 0.50",
            "2026-12-31, 2027-01-02, 3, 1, 0.33",
            "2028-02-28, 2028-03-01, 3, 1, 0.33"
    })
    void deveConsiderarTodosOsDiasInclusivosEArredondarComHalfUp(
            LocalDate inicio, LocalDate fim, int quantidadeDias, long quantidade, String media) {
        var contagens = List.of(contagem(inicio.toString(), "CONCLUIDO", quantidade));
        when(agendamentoRepository.contarPorDiaEStatus(inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay()))
                .thenReturn(contagens);

        var resposta = servico("2026-11-13T12:00:00Z").consultarAgendamentos(inicio, fim);

        assertThat(resposta.dias()).hasSize(quantidadeDias);
        assertThat(resposta.dias().getLast().data()).isEqualTo(fim);
        assertThat(resposta.totalAgendamentos()).isEqualTo(quantidade);
        assertThat(resposta.mediaPorDia()).isEqualByComparingTo(media).hasScaleOf(2);
        assertThat(resposta.totaisPorStatus()).isEqualTo(new AgendamentosTotaisStatusResponse(0, 0, 0, quantidade));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL, 2026-11-13",
            "2026-11-13, NULL",
            "2026-11-14, 2026-11-13",
            "+999999999-12-31, +999999999-12-31"
    }, nullValues = "NULL")
    void deveRejeitarIntervaloInvalidoAntesDeConsultarBanco(LocalDate inicio, LocalDate fim) {
        assertThatThrownBy(() -> servico("2026-11-13T12:00:00Z").consultarAgendamentos(inicio, fim))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        excecao -> assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(agendamentoRepository, keycloakAdminService, prestadorRepository);
    }

    @Test
    void devePropagarFalhaDoBancoSemInventarResumoComZeros() {
        var falha = new DataAccessResourceFailureException("Banco indisponível");
        when(agendamentoRepository.contarPorDiaEStatus(any(), any())).thenThrow(falha);

        assertThatThrownBy(() -> servico("2026-11-13T12:00:00Z").consultarAgendamentos(null, null))
                .isSameAs(falha);
        verifyNoInteractions(keycloakAdminService, prestadorRepository);
    }

    private AgendamentoContagemDiaria contagem(String data, String status, long quantidade) {
        var contagem = mock(AgendamentoContagemDiaria.class);
        when(contagem.getData()).thenReturn(LocalDate.parse(data));
        when(contagem.getStatus()).thenReturn(status);
        when(contagem.getQuantidade()).thenReturn(quantidade);
        return contagem;
    }

    private AdminDashboardService servico(String instante) {
        return new AdminDashboardService(keycloakAdminService, prestadorRepository, agendamentoRepository,
                Clock.fixed(Instant.parse(instante), ZoneOffset.UTC));
    }
}
