package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class PerfilUsuarioServiceTest {

    @Mock
    private PerfilUsuarioRepository perfilUsuarioRepository;

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
}
