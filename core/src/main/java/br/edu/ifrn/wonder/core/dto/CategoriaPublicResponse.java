package br.edu.ifrn.wonder.core.dto;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoriaPublicResponse(
        Long id,
        String nome,
        String descricao,
        String status,
        @JsonProperty("foto_url")
        String fotoUrl
) {

    public static CategoriaPublicResponse from(Categoria categoria) {
        return new CategoriaPublicResponse(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                formatStatus(categoria.getStatus()),
                categoria.getFotoUrl()
        );
    }

    private static String formatStatus(StatusCategoria status) {
        return status == null ? null : status.name().toLowerCase();
    }
}
