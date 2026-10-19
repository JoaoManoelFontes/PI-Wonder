package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDate;

public record DiaDisponivelResponse(
        LocalDate data,
        boolean disponivel
) {
}
