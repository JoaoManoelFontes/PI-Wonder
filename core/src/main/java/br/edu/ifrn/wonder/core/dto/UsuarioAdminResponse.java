package br.edu.ifrn.wonder.core.dto;

import java.util.Map;
import java.util.UUID;

public record UsuarioAdminResponse(UUID id, String username, String nome, String sobrenome,
        String email, boolean ativo) {

    public static UsuarioAdminResponse from(Map<String, Object> usuario) {
        return new UsuarioAdminResponse(UUID.fromString((String) usuario.get("id")),
                (String) usuario.get("username"), (String) usuario.get("firstName"),
                (String) usuario.get("lastName"), (String) usuario.get("email"),
                Boolean.TRUE.equals(usuario.get("enabled")));
    }
}
