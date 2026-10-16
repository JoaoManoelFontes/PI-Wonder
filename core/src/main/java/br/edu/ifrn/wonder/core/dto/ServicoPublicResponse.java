package br.edu.ifrn.wonder.core.dto;

import java.math.BigDecimal;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.Servico;

public record ServicoPublicResponse(
        Long id,
        @JsonProperty("prestador_id") Long prestadorId,
        @JsonProperty("categoria_id") Long categoriaId,
        String nome,
        BigDecimal preco,
        @JsonProperty("duracao_min") Integer duracaoMin,
        String status,
        @JsonProperty("foto_url") String fotoUrl
) {

    public static ServicoPublicResponse from(Servico servico) {
        return new ServicoPublicResponse(
                servico.getId(),
                servico.getPrestador().getId(),
                servico.getCategoria() == null ? null : servico.getCategoria().getId(),
                servico.getNome(),
                servico.getPreco(),
                servico.getDuracaoMin(),
                servico.getStatus() == null ? null : servico.getStatus().name().toLowerCase(Locale.ROOT),
                servico.getFotoUrl()
        );
    }
}
