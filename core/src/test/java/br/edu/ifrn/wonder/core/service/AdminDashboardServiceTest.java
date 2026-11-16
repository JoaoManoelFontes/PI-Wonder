package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private KeycloakAdminService keycloakAdminService;
    @Mock
    private PrestadorRepository prestadorRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;

    @ParameterizedTest
    @CsvSource({
            "2026-12-01T01:00:00Z, 2026-11, 2026-11-01T00:00, 2026-12-01T00:00",
            "2026-12-15T12:00:00Z, 2026-12, 2026-12-01T00:00, 2027-01-01T00:00",
            "2027-01-01T03:00:00Z, 2027-01, 2027-01-01T00:00, 2027-02-01T00:00",
            "2028-02-29T12:00:00Z, 2028-02, 2028-02-01T00:00, 2028-03-01T00:00"
    })
    void deveContarConcluidosNoMesDeSaoPauloMesmoComRelogioEmUtc(
            String instante, String mes, LocalDateTime inicio, LocalDateTime fim) {
        when(keycloakAdminService.contarUsuarios()).thenReturn(1248L);
        when(prestadorRepository.count()).thenReturn(86L);
        when(prestadorRepository.countByStatus(StatusPrestador.PENDENTE)).thenReturn(7L);
        when(agendamentoRepository.countByStatusAndInicioGreaterThanEqualAndInicioLessThan(
                StatusAgendamento.CONCLUIDO, inicio, fim)).thenReturn(342L);

        var resposta = servico(instante).consultarIndicadores();

        assertThat(resposta.totalUsuarios()).isEqualTo(1248);
        assertThat(resposta.totalPrestadores()).isEqualTo(86);
        assertThat(resposta.prestadoresPendentes()).isEqualTo(7);
        assertThat(resposta.atendimentosConcluidosMes()).isEqualTo(342);
        assertThat(resposta.mesReferencia()).isEqualTo(mes);
        verify(agendamentoRepository).countByStatusAndInicioGreaterThanEqualAndInicioLessThan(
                StatusAgendamento.CONCLUIDO, inicio, fim);
    }

    @Test
    void deveInterromperConsultaQuandoFonteDeUsuariosFalhar() {
        var falha = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        when(keycloakAdminService.contarUsuarios()).thenThrow(falha);

        assertThatThrownBy(() -> servico("2026-11-15T12:00:00Z").consultarIndicadores())
                .isSameAs(falha);
        verifyNoInteractions(prestadorRepository, agendamentoRepository);
    }

    private AdminDashboardService servico(String instante) {
        Clock relogio = Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
        return new AdminDashboardService(keycloakAdminService, prestadorRepository,
                agendamentoRepository, relogio);
    }
}
