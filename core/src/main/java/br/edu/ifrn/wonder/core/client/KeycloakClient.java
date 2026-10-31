package br.edu.ifrn.wonder.core.client;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import br.edu.ifrn.wonder.core.config.KeycloakProperties;
import br.edu.ifrn.wonder.core.dto.KeycloakRoleResponse;
import br.edu.ifrn.wonder.core.dto.KeycloakTokenResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KeycloakClient {

    private final RestClient keycloakRestClient;
    private final KeycloakProperties propriedades;

    public String obterToken() {
        if (!StringUtils.hasText(propriedades.clientSecret())) {
            throw new IllegalStateException("Secret do client Keycloak não configurado");
        }

        var formulario = new LinkedMultiValueMap<String, String>();
        formulario.add("grant_type", "client_credentials");
        formulario.add("client_id", propriedades.clientId());
        formulario.add("client_secret", propriedades.clientSecret());

        KeycloakTokenResponse resposta = keycloakRestClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", propriedades.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario)
                .retrieve()
                .body(KeycloakTokenResponse.class);

        if (resposta == null || !StringUtils.hasText(resposta.accessToken())) {
            throw new IllegalStateException("Keycloak não retornou um access token");
        }
        return resposta.accessToken();
    }

    public KeycloakRoleResponse buscarRole(String token, String nomeRole) {
        KeycloakRoleResponse resposta = keycloakRestClient.get()
                .uri("/admin/realms/{realm}/roles/{role}", propriedades.realm(), nomeRole)
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                .retrieve()
                .body(KeycloakRoleResponse.class);

        if (resposta == null || !StringUtils.hasText(resposta.id()) || !nomeRole.equals(resposta.name())) {
            throw new IllegalStateException("Keycloak não retornou a role solicitada");
        }
        return resposta;
    }

    public void adicionarRole(UUID usuarioId, String token, KeycloakRoleResponse role) {
        keycloakRestClient.post()
                .uri("/admin/realms/{realm}/users/{usuarioId}/role-mappings/realm",
                        propriedades.realm(), usuarioId)
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }
}
