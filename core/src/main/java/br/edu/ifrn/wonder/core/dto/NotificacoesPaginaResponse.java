package br.edu.ifrn.wonder.core.dto;

import java.util.List;

public record NotificacoesPaginaResponse(
        List<NotificacaoResponse> itens,
        int pagina,
        int limite,
        long total,
        boolean temProxima) {
}
