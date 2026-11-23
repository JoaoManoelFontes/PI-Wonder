package br.edu.ifrn.wonder.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record ExportacaoConcluida(UUID exportacaoId, String tipo, String email,
        String nome, String nomeArquivo, String arquivoUrl, Instant expiraEm) {
}
