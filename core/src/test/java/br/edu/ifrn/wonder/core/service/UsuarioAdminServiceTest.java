package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.dto.AtualizacaoUsuarioAdminRequest;

class UsuarioAdminServiceTest {
    final KeycloakClient cliente = mock(KeycloakClient.class);
    final UsuarioAdminService servico = new UsuarioAdminService(cliente);
    final UUID id = UUID.randomUUID();

    @Test
    void devePreservarAtributosEInformarFalhaParcialDoLogoutPermitindoNovaTentativa() {
        when(cliente.obterToken()).thenReturn("token");
        var original = Map.<String, Object>of("id", id.toString(), "username", "maria", "enabled", true,
                "attributes", Map.of("cidade", java.util.List.of("Natal")), "email", "maria@example.com");
        when(cliente.buscarUsuario("token", id)).thenReturn(original);
        doThrow(new ResourceAccessException("indisponível")).doNothing().when(cliente).encerrarSessoes("token", id);
        assertThatThrownBy(() -> servico.atualizarStatus(id, false, UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        servico.atualizarStatus(id, false, UUID.randomUUID());
        verify(cliente, times(2)).atualizarUsuario(eq("token"), eq(id), argThat(usuario ->
                Boolean.FALSE.equals(usuario.get("enabled")) && original.get("attributes").equals(usuario.get("attributes"))));
        verify(cliente, times(2)).encerrarSessoes("token", id);
        // Não há rollback para enabled=true quando o logout falha.
        verify(cliente, never()).atualizarUsuario(anyString(), any(), argThat(u -> Boolean.TRUE.equals(u.get("enabled"))));
    }

    @Test
    void deveImpedirAlteracaoDeContaDeServicoEAutoDesativacao() {
        assertThatThrownBy(() -> servico.atualizarStatus(id, false, id)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(cliente);
        when(cliente.obterToken()).thenReturn("token");
        when(cliente.buscarUsuario("token", id)).thenReturn(Map.of("serviceAccountClientId", "wonder-core"));
        assertThatThrownBy(() -> servico.atualizar(id, new AtualizacaoUsuarioAdminRequest("Nome", "", "teste@example.com")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(cliente, never()).atualizarUsuario(anyString(), any(), any());
    }

    @Test
    void deveFalharSemAlterarContaQuandoTokenDoServicoEstiverIndisponivel() {
        when(cliente.obterToken()).thenThrow(new ResourceAccessException("indisponível"));
        assertThatThrownBy(() -> servico.atualizarStatus(id, false, UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(cliente, never()).atualizarUsuario(anyString(), any(), any());
    }
}
