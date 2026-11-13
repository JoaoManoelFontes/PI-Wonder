package br.edu.ifrn.wonder.core.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import br.edu.ifrn.wonder.core.config.KeycloakProperties;
import br.edu.ifrn.wonder.core.dto.KeycloakRoleResponse;
import br.edu.ifrn.wonder.core.dto.KeycloakTokenResponse;
import br.edu.ifrn.wonder.core.dto.KeycloakUsuarioResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KeycloakClient {

    private final RestClient keycloakRestClient;
    private final KeycloakProperties propriedades;

    public List<Map<String, Object>> listarUsuarios(String token, String busca, int inicio, int limite) {
        List<Map<String, Object>> resposta = keycloakRestClient.get()
                .uri(uri -> uri.path("/admin/realms/{realm}/users")
                        .queryParam("search", "{busca}").queryParam("first", inicio).queryParam("max", limite)
                        .queryParam("briefRepresentation", false)
                        .build(Map.of("realm", propriedades.realm(), "busca", busca)))
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token)).retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        if (resposta == null) {
            throw new IllegalStateException("Keycloak não retornou os usuários");
        }
        return resposta;
    }

    public Map<String, Object> buscarUsuario(String token, UUID id) {
        Map<String, Object> resposta = keycloakRestClient.get()
                .uri("/admin/realms/{realm}/users/{id}", propriedades.realm(), id)
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token)).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        if (resposta == null) {
            throw new IllegalStateException("Keycloak não retornou o usuário");
        }
        return resposta;
    }

    public void atualizarUsuario(String token, UUID id, Map<String, Object> usuario) {
        keycloakRestClient.put().uri("/admin/realms/{realm}/users/{id}", propriedades.realm(), id)
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(usuario).retrieve().toBodilessEntity();
    }

    public void encerrarSessoes(String token, UUID id) {
        keycloakRestClient.post().uri("/admin/realms/{realm}/users/{id}/logout", propriedades.realm(), id)
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token)).retrieve().toBodilessEntity();
    }

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

    public String buscarEmailPorKeycloakId(UUID keycloakId) {
        String token = obterToken();
        KeycloakUsuarioResponse resposta = keycloakRestClient.get()
                .uri("/admin/realms/{realm}/users/{usuarioId}", propriedades.realm(), keycloakId)
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                .retrieve()
                .body(KeycloakUsuarioResponse.class);

        if (resposta == null || !StringUtils.hasText(resposta.email())) {
            throw new IllegalStateException("Keycloak não retornou o email do usuário");
        }
        return resposta.email().trim();
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

    public List<KeycloakUsuarioResponse> listarUsuariosPorRole(String token, String nomeRole) {
        int tamanhoPagina = 100;
        List<KeycloakUsuarioResponse> usuarios = new ArrayList<>();
        for (int inicio = 0; ; inicio += tamanhoPagina) {
            KeycloakUsuarioResponse[] pagina = keycloakRestClient.get()
                    .uri("/admin/realms/{realm}/roles/{role}/users?first={first}&max={max}",
                            propriedades.realm(), nomeRole, inicio, tamanhoPagina)
                    .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                    .retrieve()
                    .body(KeycloakUsuarioResponse[].class);

            if (pagina == null) {
                throw new IllegalStateException("Keycloak não retornou a lista de usuários da role");
            }
            usuarios.addAll(List.of(pagina));
            if (pagina.length < tamanhoPagina) {
                return usuarios;
            }
        }
    }
}
