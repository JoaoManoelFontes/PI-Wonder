package br.edu.ifrn.wonder.worker.dto;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PrestadoresSnapshot(List<Prestador> prestadores) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prestador(Long id, @JsonProperty("nome_estab") String nomeEstab,
            @JsonProperty("responsavel_nome") String responsavelNome, Situacao status,
            @JsonProperty("criado_em") Instant criadoEm) {}

    public enum Situacao {
        rascunho("Rascunho"), pendente("Pendente"), ativo("Ativo"), rejeitado("Rejeitado"), suspenso("Suspenso");
        private final String descricao;
        Situacao(String descricao) { this.descricao = descricao; }
        public String descricao() { return descricao; }
    }
}
