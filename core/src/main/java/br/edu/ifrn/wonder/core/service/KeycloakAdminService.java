package br.edu.ifrn.wonder.core.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.dto.KeycloakRoleResponse;
import br.edu.ifrn.wonder.core.dto.KeycloakUsuarioResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final KeycloakClient keycloakClient;

    public List<String> listarEmailsAdministradores() {
        try {
            String token = keycloakClient.obterToken();
            return keycloakClient.listarUsuariosPorRole(token, "ADMIN").stream()
                    .map(KeycloakUsuarioResponse::email)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (RestClientException | IllegalStateException excecao) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível consultar os administradores no Keycloak", excecao);
        }
    }

    public String buscarEmailPorKeycloakId(UUID keycloakId) {
        try {
            return keycloakClient.buscarEmailPorKeycloakId(keycloakId);
        } catch (RestClientException | IllegalStateException excecao) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível consultar o email do usuário no Keycloak", excecao);
        }
    }

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
