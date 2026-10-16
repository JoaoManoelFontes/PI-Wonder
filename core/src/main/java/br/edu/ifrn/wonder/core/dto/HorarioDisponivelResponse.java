package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDateTime;

public record HorarioDisponivelResponse(LocalDateTime inicio, LocalDateTime fim) {
}
