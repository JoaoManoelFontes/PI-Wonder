package br.edu.ifrn.wonder.worker.dto;

import java.time.Instant;
import java.util.UUID;

public record ExportacaoConcluida(UUID exportacaoId, ExportacaoSolicitada.Tipo tipo, String email,
        String nome, String nomeArquivo, String arquivoUrl, Instant expiraEm) {}
