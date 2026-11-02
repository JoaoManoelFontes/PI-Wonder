package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.dto.AtualizacaoStatusAgendamentoRequest;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HistoricoAgendamentoRepository;

@ExtendWith(MockitoExtension.class)
class AgendamentoStatusServiceTest {

    @Mock private AgendamentoRepository agendamentoRepository;
    @Mock private HistoricoAgendamentoRepository historicoRepository;
    @InjectMocks private AgendamentoService service;

    @ParameterizedTest
    @CsvSource({"899,false", "900,true", "901,true"})
    void deveRespeitarLimiteExatoDeQuinzeMinutos(long antecedenciaSegundos, boolean permitido) {
        LocalDateTime agora = LocalDateTime.of(2026, 11, 2, 9, 0);
        UUID clienteId = UUID.randomUUID();
        Agendamento agendamento = Agendamento.builder().id(1L)
                .cliente(new PerfilUsuario(clienteId))
                .prestador(Prestador.builder().perfilUsuario(new PerfilUsuario(UUID.randomUUID())).build())
                .inicio(agora.plusSeconds(antecedenciaSegundos)).status(StatusAgendamento.PENDENTE).build();
        when(agendamentoRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(agendamento));

        try (var tempo = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            tempo.when(LocalDateTime::now).thenReturn(agora);
            var request = new AtualizacaoStatusAgendamentoRequest("cancelado", "Mudança de planos");
            if (permitido) {
                assertThat(service.atualizarStatus(1L, clienteId, false, false, request).getStatus())
                        .isEqualTo(StatusAgendamento.CANCELADO);
            } else {
                assertThatThrownBy(() -> service.atualizarStatus(1L, clienteId, false, false, request))
                        .isInstanceOfSatisfying(ResponseStatusException.class,
                                erro -> assertThat(erro.getStatusCode().value()).isEqualTo(409));
                assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.PENDENTE);
                verifyNoInteractions(historicoRepository);
            }
        }
    }
}
