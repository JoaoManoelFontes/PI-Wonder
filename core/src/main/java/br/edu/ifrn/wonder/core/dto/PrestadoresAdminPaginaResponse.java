package br.edu.ifrn.wonder.core.dto;

import java.util.List;

public record PrestadoresAdminPaginaResponse(List<PrestadorAdminItemResponse> prestadores,
        int inicio, int limite, boolean temProxima, long total) {
}
