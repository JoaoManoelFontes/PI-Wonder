package br.edu.ifrn.wonder.worker.dto;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ExportacaoSolicitada(UUID exportacaoId, Tipo tipo, JsonNode dados, String email, String nome) {
    public enum Tipo { USUARIOS, PRESTADORES, AGENDAMENTOS }
}
