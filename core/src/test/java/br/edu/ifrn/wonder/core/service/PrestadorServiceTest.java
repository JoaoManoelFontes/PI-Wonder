package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.dto.CadastroPrestadorRequest;
import br.edu.ifrn.wonder.core.dto.AtualizacaoStatusPrestadorRequest;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.dto.ProviderRequestCreated;
import br.edu.ifrn.wonder.core.dto.ProviderRequestCreated.DadosPrestador;
import br.edu.ifrn.wonder.core.messaging.RabbitProvider;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PrestadorServiceTest {

    private static final UUID KEYCLOAK_ID = UUID.fromString("c2d3bc2f-b55a-40d7-9f2c-cd8d9c9ad25a");
    private static final UUID PERFIL_USUARIO_ID = UUID.fromString("9f4c1180-c5a2-4354-b0dd-2f001a24d19a");
    private static final UUID ADMIN_ID = UUID.fromString("e3deec3e-6509-4de8-9703-70679fdc074b");

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private PrestadorRepository prestadorRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private HorarioAtendimentoRepository horarioAtendimentoRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private PrestadorFotoService prestadorFotoService;

    @Mock
    private RabbitProvider rabbitProvider;

    @InjectMocks
    private PrestadorService prestadorService;

    @BeforeEach
    void iniciarSincronizacaoTransacao() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void limparSincronizacaoTransacao() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void deveNotificarAdministradoresSomenteAposCommitDoCadastro() {
        configurarCadastro();
        when(keycloakAdminService.listarEmailsAdministradores()).thenReturn(List.of("admin@example.com"));

        prestadorService.cadastrarPrestador(KEYCLOAK_ID, request(), null);

        verifyNoInteractions(keycloakAdminService, rabbitProvider);
        confirmarTransacao();
        verify(rabbitProvider).sendProviderRequestCreated(new ProviderRequestCreated(List.of("admin@example.com"),
                new DadosPrestador(42L, "Studio Wonder", "12345678900", "Natal", "RN")));
    }

    @Test
    void naoDeveConsultarKeycloakOuPublicarQuandoCadastroSofrerRollback() {
        configurarCadastro();
        prestadorService.cadastrarPrestador(KEYCLOAK_ID, request(), null);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().forEach(sincronizacao ->
                sincronizacao.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(keycloakAdminService, rabbitProvider);
    }

    @Test
    void naoDevePublicarEventoSemDestinatarios() {
        configurarCadastro();
        when(keycloakAdminService.listarEmailsAdministradores()).thenReturn(List.of());
        prestadorService.cadastrarPrestador(KEYCLOAK_ID, request(), null);

        confirmarTransacao();

        verifyNoInteractions(rabbitProvider);
    }

    @Test
    void devePreservarCadastroQuandoConsultaDosAdministradoresFalharAposCommit() {
        configurarCadastro();
        when(keycloakAdminService.listarEmailsAdministradores())
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        Prestador prestador = prestadorService.cadastrarPrestador(KEYCLOAK_ID, request(), null);

        confirmarTransacao();

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
        verifyNoInteractions(rabbitProvider);
    }

    @Test
    void devePreservarCadastroQuandoPublicacaoFalharAposCommit() {
        configurarCadastro();
        when(keycloakAdminService.listarEmailsAdministradores()).thenReturn(List.of("admin@example.com"));
        doThrow(new AmqpConnectException(new java.net.ConnectException("RabbitMQ indisponível")))
                .when(rabbitProvider).sendProviderRequestCreated(any());
        Prestador prestador = prestadorService.cadastrarPrestador(KEYCLOAK_ID, request(), null);

        confirmarTransacao();

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
    }

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, names = {"RASCUNHO", "REJEITADO"})
    void deveNotificarNovoEnvioParaAprovacaoAposCommit(StatusPrestador status) {
        Prestador prestador = prestadorPendente();
        prestador.setId(42L);
        prestador.setStatus(status);
        when(prestadorRepository.buscarPorIdComBloqueio(42L)).thenReturn(Optional.of(prestador));
        Categoria categoria = Categoria.builder().status(StatusCategoria.ATIVA).build();
        when(servicoRepository.findByPrestadorIdAndStatusOrderByNomeAsc(42L, StatusServico.ATIVO))
                .thenReturn(List.of(Servico.builder().categoria(categoria).status(StatusServico.ATIVO).build()));
        when(horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(42L))
                .thenReturn(List.of(new HorarioAtendimento()));
        when(keycloakAdminService.listarEmailsAdministradores()).thenReturn(List.of("admin@example.com"));

        prestadorService.enviarParaAprovacao(42L, KEYCLOAK_ID);

        verifyNoInteractions(keycloakAdminService, rabbitProvider);
        confirmarTransacao();
        verify(rabbitProvider).sendProviderRequestCreated(new ProviderRequestCreated(List.of("admin@example.com"),
                new DadosPrestador(42L, "Studio Wonder", "12345678900", "Natal", "RN")));
    }

    private void confirmarTransacao() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private void configurarCadastro() {
        when(perfilUsuarioService.buscarOuCriarPorKeycloakId(KEYCLOAK_ID)).thenReturn(perfilUsuario());
        when(prestadorRepository.save(any(Prestador.class))).thenAnswer(invocacao -> {
            Prestador prestador = invocacao.getArgument(0);
            prestador.setId(42L);
            return prestador;
        });
    }

    @Test
    void deveBuscarPrestadorVinculadoAoUsuarioAutenticadoSemFiltroDePublicacao() {
        Prestador prestador = prestadorPendente();
        when(prestadorRepository.findByPerfilUsuarioKeycloakId(KEYCLOAK_ID))
                .thenReturn(Optional.of(prestador));

        assertThat(prestadorService.buscarPrestadorDoUsuario(KEYCLOAK_ID)).isSameAs(prestador);
    }

    @Test
    void deveRetornar404QuandoUsuarioNaoPossuiPrestador() {
        when(prestadorRepository.findByPerfilUsuarioKeycloakId(KEYCLOAK_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> prestadorService.buscarPrestadorDoUsuario(KEYCLOAK_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deveCadastrarPrestadorInicialmenteInativo() {
        PerfilUsuario perfilUsuario = perfilUsuario();
        CadastroPrestadorRequest request = request();

        when(perfilUsuarioService.buscarOuCriarPorKeycloakId(KEYCLOAK_ID))
                .thenReturn(perfilUsuario);
        when(prestadorRepository.findByPerfilUsuarioId(PERFIL_USUARIO_ID))
                .thenReturn(Optional.empty());
        when(prestadorRepository.save(any(Prestador.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Prestador.class));

        Prestador resultado = prestadorService.cadastrarPrestador(KEYCLOAK_ID, request, null);

        assertThat(resultado.getPerfilUsuario()).isSameAs(perfilUsuario);
        assertThat(resultado.getNomeEstab()).isEqualTo("Studio Wonder");
        assertThat(resultado.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
        assertThat(resultado.isAtivo()).isFalse();
        assertThat(resultado.getEnviadoEm()).isNotNull();
        assertThat(resultado.getFotoUrl()).isNull();
        verifyNoInteractions(prestadorFotoService);
    }

    @Test
    void deveRejeitarFotoInvalidaAntesDeCriarCadastro() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.png", "image/png", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> prestadorService.cadastrarPrestador(KEYCLOAK_ID, request(), arquivo))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(perfilUsuarioService, prestadorRepository, prestadorFotoService);
    }

    @Test
    void deveRejeitarCadastroQuandoUsuarioJaPossuiPrestador() {
        PerfilUsuario perfilUsuario = perfilUsuario();
        Prestador prestadorExistente = Prestador.builder()
                .perfilUsuario(perfilUsuario)
                .nomeEstab("Studio Existente")
                .build();

        when(perfilUsuarioService.buscarOuCriarPorKeycloakId(KEYCLOAK_ID))
                .thenReturn(perfilUsuario);
        when(prestadorRepository.findByPerfilUsuarioId(PERFIL_USUARIO_ID))
                .thenReturn(Optional.of(prestadorExistente));

        assertThatThrownBy(() -> prestadorService.cadastrarPrestador(KEYCLOAK_ID, request(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        verify(prestadorRepository, never()).save(any(Prestador.class));
    }

    @Test
    void deveListarSolicitacoesPendentes() {
        Prestador prestador = prestadorPendente();

        when(prestadorRepository.findByStatusOrderByEnviadoEmAsc(StatusPrestador.PENDENTE))
                .thenReturn(List.of(prestador));

        List<Prestador> resultado = prestadorService.listarSolicitacoesPendentes();

        assertThat(resultado).containsExactly(prestador);
    }

    @Test
    void deveDetalharPrestadorParaAdmin() {
        Prestador prestador = prestadorPendente();

        when(prestadorRepository.findById(1L))
                .thenReturn(Optional.of(prestador));

        Prestador resultado = prestadorService.detalharPrestadorAdmin(1L);

        assertThat(resultado).isSameAs(prestador);
    }

    @Test
    void deveAprovarSolicitacaoPendente() {
        Prestador prestador = prestadorPendente();
        permitirAprovacao(prestador);

        when(prestadorRepository.buscarPorIdComBloqueio(1L))
                .thenReturn(Optional.of(prestador));

        doAnswer(invocacao -> {
            assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
            assertThat(prestador.isAtivo()).isFalse();
            return null;
        }).when(keycloakAdminService).adicionarRoleProvider(KEYCLOAK_ID);

        Prestador resultado = prestadorService.atualizarStatusAdmin(
                1L,
                new AtualizacaoStatusPrestadorRequest("ativo", null),
                ADMIN_ID
        );

        assertThat(resultado.getStatus()).isEqualTo(StatusPrestador.ATIVO);
        assertThat(resultado.isAtivo()).isTrue();
        assertThat(resultado.getAprovadoEm()).isNotNull();
        assertThat(resultado.getAprovadoPor()).isEqualTo(ADMIN_ID.toString());
        assertThat(resultado.getMotivoRejeicao()).isNull();
        verify(keycloakAdminService).adicionarRoleProvider(KEYCLOAK_ID);
    }

    @Test
    void devePreservarSolicitacaoPendenteQuandoKeycloakFalhar() {
        Prestador prestador = prestadorPendente();
        permitirAprovacao(prestador);
        prestador.setMotivoRejeicao("Histórico anterior");
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));
        var falha = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        doThrow(falha).when(keycloakAdminService).adicionarRoleProvider(KEYCLOAK_ID);

        assertThatThrownBy(() -> prestadorService.atualizarStatusAdmin(
                1L,
                new AtualizacaoStatusPrestadorRequest("ativo", null),
                ADMIN_ID
        ))
                .isSameAs(falha);

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
        assertThat(prestador.isAtivo()).isFalse();
        assertThat(prestador.getAprovadoEm()).isNull();
        assertThat(prestador.getAprovadoPor()).isNull();
        assertThat(prestador.getMotivoRejeicao()).isEqualTo("Histórico anterior");
    }

    @Test
    void naoDeveChamarKeycloakQuandoSolicitacaoNaoExistir() {
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prestadorService.atualizarStatusAdmin(
                1L,
                new AtualizacaoStatusPrestadorRequest("ativo", null),
                ADMIN_ID
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void deveReprovarSolicitacaoPendente() {
        Prestador prestador = prestadorPendente();

        when(prestadorRepository.buscarPorIdComBloqueio(1L))
                .thenReturn(Optional.of(prestador));

        Prestador resultado = prestadorService.atualizarStatusAdmin(
                1L,
                new AtualizacaoStatusPrestadorRequest("rejeitado", "Cadastro inconsistente"),
                ADMIN_ID
        );

        assertThat(resultado.getStatus()).isEqualTo(StatusPrestador.REJEITADO);
        assertThat(resultado.isAtivo()).isFalse();
        assertThat(resultado.getAprovadoEm()).isNull();
        assertThat(resultado.getAprovadoPor()).isEqualTo(ADMIN_ID.toString());
        assertThat(resultado.getMotivoRejeicao()).isEqualTo("Cadastro inconsistente");
        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void deveRetornarNotFoundQuandoSolicitacaoNaoExiste() {
        when(prestadorRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> prestadorService.detalharPrestadorAdmin(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deveRetornarConflictQuandoPrestadorNaoEstaPendente() {
        Prestador prestador = prestadorPendente();
        prestador.setStatus(StatusPrestador.ATIVO);

        when(prestadorRepository.buscarPorIdComBloqueio(1L))
                .thenReturn(Optional.of(prestador));

        assertThatThrownBy(() -> prestadorService.atualizarStatusAdmin(
                1L,
                new AtualizacaoStatusPrestadorRequest("ativo", null),
                ADMIN_ID
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void naoDevePermitirAutoaprovacaoNemChamarKeycloak() {
        Prestador prestador = prestadorPendente();
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));

        assertThatThrownBy(() -> prestadorService.atualizarStatusAdmin(
                1L,
                new AtualizacaoStatusPrestadorRequest("ativo", null),
                KEYCLOAK_ID
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
        assertThat(prestador.isAtivo()).isFalse();
        verifyNoInteractions(keycloakAdminService);
    }

    @ParameterizedTest
    @CsvSource({"PENDENTE, rascunho, RASCUNHO", "ATIVO, suspenso, SUSPENSO"})
    void naoDeveChamarKeycloakAoCorrigirOuSuspender(
            StatusPrestador statusAtual, String novoStatus, StatusPrestador statusEsperado) {
        Prestador prestador = prestadorPendente();
        prestador.setStatus(statusAtual);
        prestador.setAtivo(statusAtual == StatusPrestador.ATIVO);
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));

        Prestador resultado = prestadorService.atualizarStatusAdmin(
                1L,
                new AtualizacaoStatusPrestadorRequest(novoStatus, "Corrigir cadastro"),
                ADMIN_ID
        );

        assertThat(resultado.getStatus()).isEqualTo(statusEsperado);
        assertThat(resultado.isAtivo()).isFalse();
        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void deveReativarPrestadorPreservandoAprovacaoOriginal() {
        Prestador prestador = prestadorPendente();
        permitirAprovacao(prestador);
        LocalDateTime aprovadoEm = LocalDateTime.of(2026, 10, 1, 10, 0);
        UUID administradorOriginalId = UUID.fromString("1a329d3d-585d-419d-9c3b-57457a1a00b6");
        prestador.setStatus(StatusPrestador.SUSPENSO);
        prestador.setAprovadoEm(aprovadoEm);
        prestador.setAprovadoPor(administradorOriginalId.toString());
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));

        doAnswer(invocacao -> {
            assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.SUSPENSO);
            assertThat(prestador.isAtivo()).isFalse();
            return null;
        }).when(keycloakAdminService).adicionarRoleProvider(KEYCLOAK_ID);

        Prestador resultado = prestadorService.atualizarStatusAdmin(
                1L,
                new AtualizacaoStatusPrestadorRequest("ativo", null),
                ADMIN_ID
        );

        assertThat(resultado.getStatus()).isEqualTo(StatusPrestador.ATIVO);
        assertThat(resultado.isAtivo()).isTrue();
        assertThat(resultado.getAprovadoEm()).isEqualTo(aprovadoEm);
        assertThat(resultado.getAprovadoPor()).isEqualTo(administradorOriginalId.toString());
        verify(keycloakAdminService).adicionarRoleProvider(KEYCLOAK_ID);
    }

    private PerfilUsuario perfilUsuario() {
        PerfilUsuario perfilUsuario = new PerfilUsuario(KEYCLOAK_ID);
        ReflectionTestUtils.setField(perfilUsuario, "id", PERFIL_USUARIO_ID);
        return perfilUsuario;
    }

    private CadastroPrestadorRequest request() {
        return new CadastroPrestadorRequest(
                "Studio Wonder",
                "12345678900",
                "Rua das Flores",
                "100",
                "Centro",
                "Natal",
                "RN",
                "Sala 1"
        );
    }

    private Prestador prestadorPendente() {
        return Prestador.builder()
                .id(1L)
                .perfilUsuario(perfilUsuario())
                .nomeEstab("Studio Wonder")
                .documento("12345678900")
                .endereco("Rua das Flores")
                .numero("100")
                .bairro("Centro")
                .cidade("Natal")
                .estado("RN")
                .complemento("Sala 1")
                .status(StatusPrestador.PENDENTE)
                .ativo(false)
                .build();
    }

    private void permitirAprovacao(Prestador prestador) {
        Categoria categoria = Categoria.builder().id(1L).nome("Cabelo")
                .status(StatusCategoria.ATIVA).build();
        Servico servico = Servico.builder().id(1L).prestador(prestador).categoria(categoria)
                .nome("Corte").preco(new java.math.BigDecimal("50.00")).duracaoMin(60)
                .status(StatusServico.ATIVO).build();
        HorarioAtendimento horario = HorarioAtendimento.builder().id(1L).prestador(prestador)
                .diaSemana(1).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(12, 0)).build();
        when(servicoRepository.findByPrestadorIdAndStatusOrderByNomeAsc(1L, StatusServico.ATIVO))
                .thenReturn(List.of(servico));
        when(horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(1L))
                .thenReturn(List.of(horario));
    }

}
