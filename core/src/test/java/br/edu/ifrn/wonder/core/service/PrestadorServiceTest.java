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
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.dto.CadastroPrestadorRequest;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PrestadorServiceTest {

    private static final UUID KEYCLOAK_ID = UUID.fromString("c2d3bc2f-b55a-40d7-9f2c-cd8d9c9ad25a");
    private static final UUID PERFIL_USUARIO_ID = UUID.fromString("9f4c1180-c5a2-4354-b0dd-2f001a24d19a");

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private PrestadorRepository prestadorRepository;

    @InjectMocks
    private PrestadorService prestadorService;

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

        Prestador resultado = prestadorService.cadastrarPrestador(KEYCLOAK_ID, request);

        assertThat(resultado.getPerfilUsuario()).isSameAs(perfilUsuario);
        assertThat(resultado.getNomeEstab()).isEqualTo("Studio Wonder");
        assertThat(resultado.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
        assertThat(resultado.isAtivo()).isFalse();
        assertThat(resultado.getEnviadoEm()).isNotNull();
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

        assertThatThrownBy(() -> prestadorService.cadastrarPrestador(KEYCLOAK_ID, request()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        verify(prestadorRepository, never()).save(any(Prestador.class));
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
                "Sala 1",
                "https://cdn.wonder.dev/studio.jpg"
        );
    }
}
