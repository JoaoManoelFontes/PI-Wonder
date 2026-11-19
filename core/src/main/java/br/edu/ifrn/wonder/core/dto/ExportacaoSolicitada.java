package br.edu.ifrn.wonder.core.dto;

import java.util.UUID;

public record ExportacaoSolicitada(UUID exportacaoId, Tipo tipo, Object dados) {

    public enum Tipo {
        USUARIOS,
        PRESTADORES,
        AGENDAMENTOS
    }
}
