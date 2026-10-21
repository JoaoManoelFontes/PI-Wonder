package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class PerfilUsuarioServiceTest {

    @Mock
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Mock
    private ArmazenamentoFotoService armazenamentoFotoService;

    @InjectMocks
    private PerfilUsuarioService perfilUsuarioService;

    @Test
    void deveRetornarPerfilExistenteQuandoEncontradoPeloKeycloakId() {
        UUID keycloakId = UUID.randomUUID();
        PerfilUsuario perfilUsuarioExistente = new PerfilUsuario(keycloakId);

        when(perfilUsuarioRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.of(perfilUsuarioExistente));

        PerfilUsuario resultado = perfilUsuarioService.buscarOuCriarPorKeycloakId(keycloakId);

        assertThat(resultado).isSameAs(perfilUsuarioExistente);
        verify(perfilUsuarioRepository, never()).saveAndFlush(any(PerfilUsuario.class));
    }

    @Test
    void deveCriarPerfilQuandoNaoEncontradoPeloKeycloakId() {
        UUID keycloakId = UUID.randomUUID();
        PerfilUsuario perfilUsuarioSalvo = new PerfilUsuario(keycloakId);

        when(perfilUsuarioRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.empty());
        when(perfilUsuarioRepository.saveAndFlush(any(PerfilUsuario.class)))
                .thenAnswer(invocation -> {
                    PerfilUsuario perfilUsuarioParaSalvar = invocation.getArgument(0, PerfilUsuario.class);
                    assertThat(perfilUsuarioParaSalvar.getKeycloakId()).isEqualTo(keycloakId);
                    return perfilUsuarioSalvo;
                });

        PerfilUsuario resultado = perfilUsuarioService.buscarOuCriarPorKeycloakId(keycloakId);

        assertThat(resultado).isSameAs(perfilUsuarioSalvo);
        verify(perfilUsuarioRepository).saveAndFlush(any(PerfilUsuario.class));
    }

    @Test
    void deveRetornarPerfilExistenteQuandoCriacaoPerdeConcorrencia() {
        UUID keycloakId = UUID.randomUUID();
        PerfilUsuario perfilUsuarioExistente = new PerfilUsuario(keycloakId);
        DataIntegrityViolationException violacao = new DataIntegrityViolationException("keycloak_id duplicado");

        when(perfilUsuarioRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.empty(), Optional.of(perfilUsuarioExistente));
        when(perfilUsuarioRepository.saveAndFlush(any(PerfilUsuario.class)))
                .thenAnswer(invocation -> {
                    throw violacao;
                });

        PerfilUsuario resultado = perfilUsuarioService.buscarOuCriarPorKeycloakId(keycloakId);

        assertThat(resultado).isSameAs(perfilUsuarioExistente);
    }

    @Test
    void deveRelancarViolacaoDeIntegridadeQuandoPerfilContinuaNaoEncontrado() {
        UUID keycloakId = UUID.randomUUID();
        DataIntegrityViolationException violacao = new DataIntegrityViolationException("keycloak_id duplicado");

        when(perfilUsuarioRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.empty());
        when(perfilUsuarioRepository.saveAndFlush(any(PerfilUsuario.class)))
                .thenAnswer(invocation -> {
                    throw violacao;
                });

        assertThatThrownBy(() -> perfilUsuarioService.buscarOuCriarPorKeycloakId(keycloakId))
                .isSameAs(violacao);
    }

    @Test
    void deveArmazenarFotoEGuardarSomenteNomeDoObjeto() {
        UUID keycloakId = UUID.randomUUID();
        UUID perfilId = UUID.randomUUID();
        PerfilUsuario perfilUsuario = perfilUsuario(keycloakId, perfilId);
        MockMultipartFile foto = fotoValida("foto.png", new byte[]{1, 2, 3});

        when(perfilUsuarioRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.of(perfilUsuario));
        when(perfilUsuarioRepository.save(perfilUsuario))
                .thenReturn(perfilUsuario);

        PerfilUsuario resultado = perfilUsuarioService.atualizar(keycloakId, foto, null);

        String nomeObjeto = "perfis/%s/foto".formatted(perfilId);
        assertThat(resultado.getFotoUrl()).isEqualTo(nomeObjeto);
        verify(armazenamentoFotoService).salvar(nomeObjeto, foto);
        verify(perfilUsuarioRepository).save(perfilUsuario);
    }

    @Test
    void naoDevePersistirFotoQuandoArmazenamentoFalhar() {
        UUID keycloakId = UUID.randomUUID();
        UUID perfilId = UUID.randomUUID();
        PerfilUsuario perfilUsuario = perfilUsuario(keycloakId, perfilId);
        MockMultipartFile foto = fotoValida("foto.png", new byte[]{1, 2, 3});
        String nomeObjeto = "perfis/%s/foto".formatted(perfilId);

        when(perfilUsuarioRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.of(perfilUsuario));
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .when(armazenamentoFotoService).salvar(nomeObjeto, foto);

        assertThatThrownBy(() -> perfilUsuarioService.atualizar(keycloakId, foto, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(perfilUsuario.getFotoUrl()).isNull();
        verify(perfilUsuarioRepository, never()).save(perfilUsuario);
    }

    @Test
    void naoDeveAtualizarFotoQuandoArquivoEstiverVazio() {
        UUID keycloakId = UUID.randomUUID();
        PerfilUsuario perfilUsuario = new PerfilUsuario(keycloakId);
        MockMultipartFile fotoVazia = fotoValida("foto.png", new byte[0]);

        when(perfilUsuarioRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.of(perfilUsuario));

        PerfilUsuario resultado = perfilUsuarioService.atualizar(keycloakId, fotoVazia, null);

        assertThat(resultado).isSameAs(perfilUsuario);
        assertThat(resultado.getFotoUrl()).isNull();
        verify(perfilUsuarioRepository, never()).save(perfilUsuario);
        verifyNoInteractions(armazenamentoFotoService);
    }

    @Test
    void deveRejeitarFormatoDeFotoNaoPermitido() {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo",
                "foto.svg",
                "image/svg+xml",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> perfilUsuarioService.atualizar(UUID.randomUUID(), arquivo, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(perfilUsuarioRepository, armazenamentoFotoService);
    }

    @Test
    void deveRejeitarFotoMaiorQueCincoMegabytes() {
        MockMultipartFile fotoGrande = fotoValida("foto.png", new byte[5 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> perfilUsuarioService.atualizar(UUID.randomUUID(), fotoGrande, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONTENT_TOO_LARGE));

        verifyNoInteractions(perfilUsuarioRepository, armazenamentoFotoService);
    }

    @Test
    void deveAtualizarNumeroTelefoneSemAlterarFoto() {
        UUID keycloakId = UUID.randomUUID();
        PerfilUsuario perfilUsuario = new PerfilUsuario(keycloakId);
        perfilUsuario.setFotoUrl("perfis/foto-existente");

        when(perfilUsuarioRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.of(perfilUsuario));
        when(perfilUsuarioRepository.save(perfilUsuario))
                .thenReturn(perfilUsuario);

        PerfilUsuario resultado = perfilUsuarioService.atualizar(keycloakId, null, "84999999999");

        assertThat(resultado.getNumeroTelefone()).isEqualTo("84999999999");
        assertThat(resultado.getFotoUrl()).isEqualTo("perfis/foto-existente");
        verify(perfilUsuarioRepository).save(perfilUsuario);
        verifyNoInteractions(armazenamentoFotoService);
    }

    @Test
    void naoDeveAtualizarNumeroTelefoneQuandoCampoEstiverEmBranco() {
        UUID keycloakId = UUID.randomUUID();
        PerfilUsuario perfilUsuario = new PerfilUsuario(keycloakId);
        perfilUsuario.setNumeroTelefone("84999999999");

        when(perfilUsuarioRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.of(perfilUsuario));

        PerfilUsuario resultado = perfilUsuarioService.atualizar(keycloakId, null, "   ");

        assertThat(resultado.getNumeroTelefone()).isEqualTo("84999999999");
        verify(perfilUsuarioRepository, never()).save(perfilUsuario);
        verifyNoInteractions(armazenamentoFotoService);
    }

    private PerfilUsuario perfilUsuario(UUID keycloakId, UUID perfilId) {
        PerfilUsuario perfilUsuario = new PerfilUsuario(keycloakId);
        ReflectionTestUtils.setField(perfilUsuario, "id", perfilId);
        return perfilUsuario;
    }

    private MockMultipartFile fotoValida(String nome, byte[] conteudo) {
        return new MockMultipartFile("arquivo", nome, "image/png", conteudo);
    }
}
