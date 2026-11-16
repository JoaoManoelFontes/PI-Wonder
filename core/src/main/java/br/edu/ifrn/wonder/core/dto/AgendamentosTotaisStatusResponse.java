package br.edu.ifrn.wonder.core.dto;

public record AgendamentosTotaisStatusResponse(
        long pendente,
        long confirmado,
        long cancelado,
        long concluido
) {

    public long total() {
        return pendente + confirmado + cancelado + concluido;
    }
}
