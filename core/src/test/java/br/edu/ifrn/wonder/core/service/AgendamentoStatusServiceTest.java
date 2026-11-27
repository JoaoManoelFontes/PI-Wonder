package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.HistoricoAgendamento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.dto.AtualizacaoStatusAgendamentoRequest;
import br.edu.ifrn.wonder.core.dto.AgendamentoNotificacaoEvent;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HistoricoAgendamentoRepository;
import br.edu.ifrn.wonder.core.messaging.RabbitProvider;

@ExtendWith(MockitoExtension.class)
class AgendamentoStatusServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 27, 9, 0);
    private static final UUID CLIENTE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TERCEIRO = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock private AgendamentoRepository agendamentoRepository;
    @Mock private HistoricoAgendamentoRepository historicoRepository;
    @Mock private RabbitProvider rabbitProvider;
    @InjectMocks private AgendamentoService service;

    @ParameterizedTest
    @CsvSource({"899,false", "900,true", "901,true"})
    void deveRespeitarLimiteExatoDeQuinzeMinutos(long antecedenciaSegundos, boolean permitido) {
        LocalDateTime agora = LocalDateTime.of(2026, 11, 2, 9, 0);
        UUID clienteId = UUID.randomUUID();
        Agendamento agendamento = Agendamento.builder().id(1L)
                .cliente(new PerfilUsuario(clienteId))
                .prestador(Prestador.builder().nomeEstab("Studio")
                        .perfilUsuario(new PerfilUsuario(UUID.randomUUID())).build())
                .servico(Servico.builder().nome("Corte").build())
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
                verifyNoInteractions(historicoRepository, rabbitProvider);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "PENDENTE,confirmado,prestador",
            "CONFIRMADO,concluido,prestador",
            "PENDENTE,cancelado,cliente",
            "CONFIRMADO,cancelado,cliente",
            "PENDENTE,cancelado,prestador",
            "CONFIRMADO,cancelado,prestador",
            "PENDENTE,confirmado,administrador",
            "CONFIRMADO,concluido,administrador",
            "PENDENTE,cancelado,administrador",
            "CONFIRMADO,cancelado,administrador",
            "PENDENTE,cancelado,cliente_prestador"
    })
    void deveAtualizarEstadoHistoricoEEventoComAutorCorreto(
            StatusAgendamento anterior, String novo, String papel) {
        Agendamento agendamento = prepararAgendamento(anterior);
        UUID autor = autor(papel);
        StatusAgendamento esperado = StatusAgendamento.valueOf(novo.toUpperCase(Locale.ROOT));

        assertThat(executar(novo, "  Mudança de planos  ", papel)).isSameAs(agendamento);
        assertThat(agendamento.getStatus()).isEqualTo(esperado);

        var historico = ArgumentCaptor.forClass(HistoricoAgendamento.class);
        verify(historicoRepository).save(historico.capture());
        assertThat(historico.getValue().getAgendamento()).isSameAs(agendamento);
        assertThat(historico.getValue().getUsuarioKeycloakId()).isEqualTo(autor);
        assertThat(historico.getValue().getStatusAnterior()).isEqualTo(anterior);
        assertThat(historico.getValue().getStatusNovo()).isEqualTo(esperado);
        assertThat(historico.getValue().getAlteradoEm()).isEqualTo(AGORA);
        assertThat(historico.getValue().getMotivo()).isEqualTo("Mudança de planos");

        String tipoAutor = switch (papel) {
            case "cliente", "cliente_prestador" -> "cliente";
            case "prestador" -> "prestador";
            default -> "administrador";
        };
        verify(rabbitProvider).sendAppointmentNotification(new AgendamentoNotificacaoEvent(
                1L, CLIENTE, PRESTADOR, "Studio", "Corte", agendamento.getInicio(),
                novo, tipoAutor, "Mudança de planos"));
    }

    @ParameterizedTest
    @CsvSource({
            "cliente,confirmado", "cliente,concluido",
            "terceiro,cancelado", "terceiro,confirmado", "terceiro,concluido",
            "terceiro_prestador,cancelado", "terceiro_prestador,confirmado", "terceiro_prestador,concluido",
            "prestador_sem_papel,cancelado", "prestador_sem_papel,confirmado", "prestador_sem_papel,concluido"
    })
    void deveRejeitarQuemNaoTemPermissaoSemAlterarEstadoOuEmitirEvento(String papel, String novo) {
        StatusAgendamento anterior = novo.equals("concluido")
                ? StatusAgendamento.CONFIRMADO : StatusAgendamento.PENDENTE;
        Agendamento agendamento = prepararAgendamento(anterior);

        verificarRejeicao(agendamento, novo, "Motivo", papel, 403);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDENTE,pendente", "PENDENTE,concluido",
            "CONFIRMADO,pendente", "CONFIRMADO,confirmado",
            "CANCELADO,pendente", "CANCELADO,confirmado", "CANCELADO,cancelado", "CANCELADO,concluido",
            "CONCLUIDO,pendente", "CONCLUIDO,confirmado", "CONCLUIDO,cancelado", "CONCLUIDO,concluido"
    })
    void deveRejeitarTransicaoInvalidaMesmoParaAdministrador(StatusAgendamento anterior, String novo) {
        Agendamento agendamento = prepararAgendamento(anterior);

        verificarRejeicao(agendamento, novo, "Motivo", "administrador", 409);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void deveExigirMotivoParaCancelarSemAlterarEstadoOuEmitirEvento(String motivo) {
        Agendamento agendamento = prepararAgendamento(StatusAgendamento.CONFIRMADO);

        verificarRejeicao(agendamento, "cancelado", motivo, "cliente", 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inexistente", " ", "confirmada"})
    void deveRejeitarStatusDesconhecidoAntesDeBuscarAgendamento(String novo) {
        assertThatThrownBy(() -> executar(novo, null, "administrador"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(agendamentoRepository, historicoRepository, rabbitProvider);
    }

    @Test
    void deveRetornar404ParaAgendamentoInexistenteSemEfeitosColaterais() {
        when(agendamentoRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executar("confirmado", null, "administrador"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(historicoRepository, rabbitProvider);
    }

    @Test
    void deveNormalizarStatusESalvarConfirmacaoSemMotivo() {
        prepararAgendamento(StatusAgendamento.PENDENTE);

        assertThat(executar("  cOnFiRmAdO  ", null, "prestador").getStatus())
                .isEqualTo(StatusAgendamento.CONFIRMADO);
        var historico = ArgumentCaptor.forClass(HistoricoAgendamento.class);
        verify(historicoRepository).save(historico.capture());
        assertThat(historico.getValue().getMotivo()).isNull();
        verify(rabbitProvider).sendAppointmentNotification(new AgendamentoNotificacaoEvent(
                1L, CLIENTE, PRESTADOR, "Studio", "Corte", AGORA.plusHours(1),
                "confirmado", "prestador", null));
    }

    private Agendamento prepararAgendamento(StatusAgendamento status) {
        Agendamento agendamento = Agendamento.builder().id(1L)
                .cliente(new PerfilUsuario(CLIENTE))
                .prestador(Prestador.builder().nomeEstab("Studio")
                        .perfilUsuario(new PerfilUsuario(PRESTADOR)).build())
                .servico(Servico.builder().nome("Corte").build())
                .inicio(AGORA.plusHours(1)).status(status).build();
        when(agendamentoRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(agendamento));
        return agendamento;
    }

    private UUID autor(String papel) {
        return switch (papel) {
            case "cliente", "cliente_prestador" -> CLIENTE;
            case "prestador", "prestador_sem_papel" -> PRESTADOR;
            case "administrador", "terceiro", "terceiro_prestador" -> TERCEIRO;
            default -> throw new IllegalArgumentException("Papel de teste desconhecido: " + papel);
        };
    }

    private Agendamento executar(String status, String motivo, String papel) {
        boolean administrador = papel.equals("administrador");
        boolean prestador = papel.equals("prestador") || papel.equals("terceiro_prestador")
                || papel.equals("cliente_prestador");
        try (var tempo = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            tempo.when(LocalDateTime::now).thenReturn(AGORA);
            return service.atualizarStatus(1L, autor(papel), administrador, prestador,
                    new AtualizacaoStatusAgendamentoRequest(status, motivo));
        }
    }

    private void verificarRejeicao(Agendamento agendamento, String status, String motivo, String papel, int http) {
        StatusAgendamento anterior = agendamento.getStatus();
        assertThatThrownBy(() -> executar(status, motivo, papel))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode().value()).isEqualTo(http));
        assertThat(agendamento.getStatus()).isEqualTo(anterior);
        verifyNoInteractions(historicoRepository, rabbitProvider);
    }
}
