package br.edu.ifrn.wonder.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.FotoEstabelecimento;

public record FotoEstabelecimentoResponse(
        Long id,
        @JsonProperty("prestador_id") Long prestadorId,
        @JsonProperty("foto_url") String fotoUrl,
        int ordem
) {
    public static FotoEstabelecimentoResponse from(FotoEstabelecimento foto, int ordem) {
        return new FotoEstabelecimentoResponse(foto.getId(), foto.getPrestador().getId(), foto.getFotoUrl(), ordem);
    }
}
