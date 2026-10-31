package br.edu.ifrn.wonder.core.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.dto.KeycloakRoleResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final KeycloakClient keycloakClient;

    public void adicionarRoleProvider(UUID usuarioId) {
        try {
            String token = keycloakClient.obterToken();
            KeycloakRoleResponse role = keycloakClient.buscarRole(token, "PROVIDER");
            keycloakClient.adicionarRole(usuarioId, token, role);
        } catch (RestClientException | IllegalStateException excecao) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível atribuir a role de prestador no Keycloak",
                    excecao
            );
        }
    }
}
