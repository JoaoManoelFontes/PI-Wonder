package br.edu.ifrn.wonder.worker.dto;

import java.util.List;
import java.util.UUID;

public record UsuariosSnapshot(List<Usuario> usuarios) {
    public record Usuario(UUID id, String username, String nome, String sobrenome, String email, Boolean ativo) {}
}
