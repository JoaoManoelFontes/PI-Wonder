package br.edu.ifrn.wonder.core.dto;

import java.util.List;

public record UsuariosAdminPaginaResponse(List<UsuarioAdminResponse> usuarios, int inicio, int limite,
        boolean temProxima) {
}
