package br.edu.ifrn.wonder.core.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizacaoStatusUsuarioRequest(@NotNull Boolean ativo) {
}
