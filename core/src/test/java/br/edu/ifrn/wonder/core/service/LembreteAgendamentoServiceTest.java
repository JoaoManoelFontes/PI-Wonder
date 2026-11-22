package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.dto.AgendamentoNotificacaoEvent;
import br.edu.ifrn.wonder.core.messaging.RabbitProvider;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.NotificacaoRepository;

@ExtendWith(MockitoExtension.class)
class LembreteAgendamentoServiceTest {

    @Mock private AgendamentoRepository agendamentoRepository;
    @Mock private NotificacaoRepository notificacaoRepository;
    @Mock private RabbitProvider rabbitProvider;

    private LembreteAgendamentoService service;
    private Agendamento agendamento;

    @BeforeEach
    void preparar() {
        service = new LembreteAgendamentoService(
                agendamentoRepository, notificacaoRepository, rabbitProvider);
        agendamento = Agendamento.builder()
                .id(20L)
                .cliente(new PerfilUsuario(UUID.randomUUID()))
                .prestador(Prestador.builder().nomeEstab("Studio Wonder")
                        .perfilUsuario(new PerfilUsuario(UUID.randomUUID())).build())
                .servico(Servico.builder().nome("Corte").build())
                .inicio(LocalDateTime.now().plusHours(2))
                .status(StatusAgendamento.CONFIRMADO)
                .build();
    }

    @Test
    void devePublicarLembreteParaAgendamentoProximo() {
        when(agendamentoRepository.findByStatusAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                eq(StatusAgendamento.CONFIRMADO), any(), any())).thenReturn(List.of(agendamento));

        service.publicarLembretes();

        ArgumentCaptor<AgendamentoNotificacaoEvent> captor =
                ArgumentCaptor.forClass(AgendamentoNotificacaoEvent.class);
        verify(rabbitProvider).sendAppointmentNotification(captor.capture());
        assertThat(captor.getValue().agendamentoId()).isEqualTo(20L);
        assertThat(captor.getValue().statusNovo()).isEqualTo("lembrete");
    }

    @Test
    void naoDeveRepetirLembreteJaPersistidoParaAmbos() {
        when(agendamentoRepository.findByStatusAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                eq(StatusAgendamento.CONFIRMADO), any(), any())).thenReturn(List.of(agendamento));
        when(notificacaoRepository.existsByChaveEventoAndUsuarioKeycloakIdAndTipo(
                any(), any(), eq("lembrete_agendamento"))).thenReturn(true);

        service.publicarLembretes();

        verify(rabbitProvider, never()).sendAppointmentNotification(any());
    }
}
