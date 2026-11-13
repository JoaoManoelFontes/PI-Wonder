package br.edu.ifrn.wonder.core.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.dto.AtualizacaoUsuarioAdminRequest;
import br.edu.ifrn.wonder.core.dto.UsuarioAdminResponse;
import br.edu.ifrn.wonder.core.dto.UsuariosAdminPaginaResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioAdminService {
    private final KeycloakClient keycloakClient;

    public UsuariosAdminPaginaResponse listar(String busca, int inicio, int limite) {
        if (inicio < 0 || limite < 1 || limite > 100 || busca.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação ou busca inválida");
        }
        return executar(() -> {
            String token = keycloakClient.obterToken();
            var usuarios = keycloakClient.listarUsuarios(token, busca.trim(), inicio, limite + 1).stream()
                    .filter(usuario -> usuario.get("serviceAccountClientId") == null)
                    .map(UsuarioAdminResponse::from).toList();
            return new UsuariosAdminPaginaResponse(usuarios.stream().limit(limite).toList(), inicio, limite,
                    usuarios.size() > limite);
        });
    }

    public UsuarioAdminResponse detalhar(UUID id) {
        return executar(() -> UsuarioAdminResponse.from(buscarPessoa(keycloakClient.obterToken(), id)));
    }

    public UsuarioAdminResponse atualizar(UUID id, AtualizacaoUsuarioAdminRequest pedido) {
        return executar(() -> {
            String token = keycloakClient.obterToken();
            Map<String, Object> usuario = buscarPessoa(token, id);
            if (!pedido.email().equals(usuario.get("email"))) {
                usuario.put("emailVerified", false);
            }
            usuario.put("firstName", pedido.nome());
            usuario.put("lastName", pedido.sobrenome());
            usuario.put("email", pedido.email());
            keycloakClient.atualizarUsuario(token, id, usuario);
            return UsuarioAdminResponse.from(buscarPessoa(token, id));
        });
    }

    public UsuarioAdminResponse atualizarStatus(UUID id, boolean ativo, UUID administradorId) {
        if (!ativo && id.equals(administradorId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é permitido desativar a própria conta");
        }
        return executar(() -> {
            String token = keycloakClient.obterToken();
            Map<String, Object> usuario = buscarPessoa(token, id);
            if (ativo && !Boolean.TRUE.equals(usuario.get("enabled"))) {
                // Uma tentativa anterior de desativação pode ter falhado apenas no logout.
                // Encerra sessões remanescentes antes de permitir um novo login.
                keycloakClient.encerrarSessoes(token, id);
            }
            usuario.put("enabled", ativo);
            keycloakClient.atualizarUsuario(token, id, usuario);
            if (!ativo) {
                // Repetir a desativação também repete o logout, permitindo recuperar uma falha parcial.
                keycloakClient.encerrarSessoes(token, id);
            }
            return UsuarioAdminResponse.from(buscarPessoa(token, id));
        });
    }

    private Map<String, Object> buscarPessoa(String token, UUID id) {
        Map<String, Object> usuario = keycloakClient.buscarUsuario(token, id);
        if (usuario.get("serviceAccountClientId") != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
        }
        return new LinkedHashMap<>(usuario);
    }

    private <T> T executar(Supplier<T> operacao) {
        try {
            return operacao.get();
        } catch (HttpClientErrorException.NotFound excecao) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado", excecao);
        } catch (HttpClientErrorException.Conflict excecao) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já utilizado por outro usuário", excecao);
        } catch (HttpClientErrorException.BadRequest excecao) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do usuário inválidos", excecao);
        } catch (RestClientException | IllegalStateException excecao) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível concluir a operação no Keycloak. Consulte o usuário antes de tentar novamente.",
                    excecao);
        }
    }
}
