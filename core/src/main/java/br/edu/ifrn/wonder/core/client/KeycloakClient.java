package br.edu.ifrn.wonder.core.client;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
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

    public List<Map<String, Object>> listarMembrosPapel(String token, String papel, int inicio, int limite) {
        var resposta = keycloakRestClient.get()
                .uri("/admin/realms/{realm}/roles/{papel}/users?first={inicio}&max={limite}&briefRepresentation=false",
                        propriedades.realm(), papel, inicio, limite)
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token)).retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        if (resposta == null) {
            throw new IllegalStateException("Keycloak não retornou os membros do papel");
        }
        return resposta;
    }

    public List<Map<String, Object>> listarPessoas(String token, String busca, String papel) {
        Map<String, Map<String, Object>> pessoas = new LinkedHashMap<>();
        for (int inicio = 0; ; inicio += 100) {
            var pagina = listarUsuarios(token, busca, inicio, 100);
            for (var usuario : pagina) {
                if (usuario.get("serviceAccountClientId") == null) {
                    pessoas.put((String) usuario.get("id"), usuario);
                }
            }
            if (pagina.size() < 100) {
                break;
            }
        }
        if (papel != null) {
            var membros = new HashSet<String>();
            for (String papelAssociado : listarPapeisQueConcedem(token, papel)) {
                for (int inicio = 0; ; inicio += 100) {
                    var pagina = listarMembrosPapel(token, papelAssociado, inicio, 100);
                    for (var usuario : pagina) {
                        membros.add((String) usuario.get("id"));
                    }
                    if (pagina.size() < 100) {
                        break;
                    }
                }
            }
            pessoas.keySet().retainAll(membros);
        }
        return new ArrayList<>(pessoas.values());
    }

    private Set<String> listarPapeisQueConcedem(String token, String papel) {
        Map<String, List<String>> composicoes = new LinkedHashMap<>();
        for (int inicio = 0; ; inicio += 100) {
            var pagina = keycloakRestClient.get()
                    .uri("/admin/realms/{realm}/roles?first={inicio}&max=100", propriedades.realm(), inicio)
                    .headers(cabecalhos -> cabecalhos.setBearerAuth(token)).retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (pagina == null) {
                throw new IllegalStateException("Keycloak não retornou os papéis");
            }
            for (var representacao : pagina) {
                if (Boolean.TRUE.equals(representacao.get("composite"))) {
                    String nome = (String) representacao.get("name");
                    var componentes = keycloakRestClient.get()
                            .uri("/admin/realms/{realm}/roles/{papel}/composites/realm", propriedades.realm(), nome)
                            .headers(cabecalhos -> cabecalhos.setBearerAuth(token)).retrieve()
                            .body(KeycloakRoleResponse[].class);
                    if (componentes == null) {
                        throw new IllegalStateException("Keycloak não retornou a composição do papel");
                    }
                    composicoes.put(nome, Arrays.stream(componentes).map(KeycloakRoleResponse::name).toList());
                }
            }
            if (pagina.size() < 100) {
                break;
            }
        }
        var papeis = new LinkedHashSet<String>();
        papeis.add(papel);
        boolean alterado;
        do {
            alterado = false;
            for (var composicao : composicoes.entrySet()) {
                if (composicao.getValue().stream().anyMatch(papeis::contains)) {
                    alterado |= papeis.add(composicao.getKey());
                }
            }
        } while (alterado);
        return papeis;
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

    public long contarUsuarios() {
        String token = obterToken();
        String resposta = keycloakRestClient.get()
                .uri("/admin/realms/{realm}/users/count", propriedades.realm())
                .headers(cabecalhos -> cabecalhos.setBearerAuth(token))
                .retrieve()
                .body(String.class);

        if (resposta == null || !resposta.strip().matches("[0-9]+")) {
            throw new IllegalStateException("Keycloak não retornou uma contagem válida de usuários");
        }
        try {
            return Long.parseLong(resposta.strip());
        } catch (NumberFormatException excecao) {
            throw new IllegalStateException("Contagem de usuários do Keycloak fora do limite suportado", excecao);
        }
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
