package br.edu.ifrn.wonder.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.edu.ifrn.wonder.notification.dto.AgendamentoNotificacaoEvent;
import br.edu.ifrn.wonder.notification.model.Notificacao;
import br.edu.ifrn.wonder.notification.repository.NotificacaoRepository;

@ExtendWith(MockitoExtension.class)
class AgendamentoNotificacaoServiceTest {

    @Mock
    private NotificacaoRepository repository;

    private AgendamentoNotificacaoService service;
    private UUID cliente;
    private UUID prestador;

    @BeforeEach
    void preparar() {
        service = new AgendamentoNotificacaoService(repository);
        cliente = UUID.randomUUID();
        prestador = UUID.randomUUID();
    }

    @Test
    void deveCriarAvisosParaClienteEPrestadorAoAgendar() {
        service.processar(evento(null, "cliente", null));

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notificacao::getUsuarioKeycloakId)
                .containsExactlyInAnyOrder(cliente, prestador);
        assertThat(captor.getAllValues()).extracting(Notificacao::getMensagem)
                .allSatisfy(mensagem -> assertThat(mensagem).contains("Corte", "Studio Wonder", "20/11/2026"));
    }

    @Test
    void deveAvisarClienteQuandoPrestadorCancelar() {
        service.processar(evento("cancelado", "prestador", "Imprevisto"));

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsuarioKeycloakId()).isEqualTo(cliente);
        assertThat(captor.getValue().getMensagem()).contains("cancelado pelo prestador", "Imprevisto");
    }

    @Test
    void deveCriarLembreteParaClienteEPrestador() {
        service.processar(evento("lembrete", "sistema", null));

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notificacao::getTipo)
                .containsOnly("lembrete_agendamento");
    }

    @Test
    void naoDeveDuplicarEventoJaPersistido() {
        when(repository.existsByChaveEventoAndUsuarioKeycloakIdAndTipo(any(), any(), any()))
                .thenReturn(true);

        service.processar(evento("confirmado", "prestador", null));

        verify(repository, never()).save(any());
    }

    @Test
    void deveRejeitarEventoInvalidoParaSeguirParaDlq() {
        assertThatThrownBy(() -> service.processar(new AgendamentoNotificacaoEvent(
                null, cliente, prestador, "Studio", "Corte", LocalDateTime.now(), null, "cliente", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AgendamentoNotificacaoEvent evento(String status, String autor, String motivo) {
        return new AgendamentoNotificacaoEvent(15L, cliente, prestador, "Studio Wonder", "Corte",
                LocalDateTime.of(2026, 11, 20, 14, 0), status, autor, motivo);
    }
}
