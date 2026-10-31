package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.dto.KeycloakRoleResponse;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminServiceTest {

    @Mock
    private KeycloakClient keycloakClient;

    @InjectMocks
    private KeycloakAdminService keycloakAdminService;

    @Test
    void deveAutenticarConsultarRoleEAdicionarAoUsuarioInformado() {
        UUID usuarioId = UUID.randomUUID();
        KeycloakRoleResponse role = new KeycloakRoleResponse("id-provider", "PROVIDER");
        when(keycloakClient.obterToken()).thenReturn("token");
        when(keycloakClient.buscarRole("token", "PROVIDER")).thenReturn(role);

        keycloakAdminService.adicionarRoleProvider(usuarioId);

        var ordem = inOrder(keycloakClient);
        ordem.verify(keycloakClient).obterToken();
        ordem.verify(keycloakClient).buscarRole("token", "PROVIDER");
        ordem.verify(keycloakClient).adicionarRole(usuarioId, "token", role);
        ordem.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void deveInterromperFluxoEInformarIndisponibilidadeQuandoUmaEtapaFalhar(int etapa) {
        UUID usuarioId = UUID.randomUUID();
        KeycloakRoleResponse role = new KeycloakRoleResponse("id-provider", "PROVIDER");
        var falha = new ResourceAccessException("Falha de comunicação");
        if (etapa == 0) {
            when(keycloakClient.obterToken()).thenThrow(falha);
        } else {
            when(keycloakClient.obterToken()).thenReturn("token");
            if (etapa == 1) {
                when(keycloakClient.buscarRole("token", "PROVIDER")).thenThrow(falha);
            } else {
                when(keycloakClient.buscarRole("token", "PROVIDER")).thenReturn(role);
                doThrow(falha).when(keycloakClient).adicionarRole(usuarioId, "token", role);
            }
        }

        assertThatThrownBy(() -> keycloakAdminService.adicionarRoleProvider(usuarioId))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao -> {
                    assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(excecao.getCause()).isSameAs(falha);
                });

        var ordem = inOrder(keycloakClient);
        ordem.verify(keycloakClient).obterToken();
        if (etapa >= 1) {
            ordem.verify(keycloakClient).buscarRole("token", "PROVIDER");
        }
        if (etapa == 2) {
            ordem.verify(keycloakClient).adicionarRole(usuarioId, "token", role);
        }
        ordem.verifyNoMoreInteractions();
    }

    @Test
    void deveInformarIndisponibilidadeQuandoConfiguracaoOuRespostaForInvalida() {
        when(keycloakClient.obterToken()).thenThrow(new IllegalStateException("Secret ausente"));

        assertThatThrownBy(() -> keycloakAdminService.adicionarRoleProvider(UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
