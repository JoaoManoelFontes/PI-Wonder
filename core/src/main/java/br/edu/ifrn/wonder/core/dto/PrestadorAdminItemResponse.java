package br.edu.ifrn.wonder.core.dto;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import br.edu.ifrn.wonder.core.domain.Prestador;

public record PrestadorAdminItemResponse(
        Long id,
        @JsonProperty("usuario_id") UUID usuarioId,
        @JsonProperty("nome_estab") String nomeEstab,
        @JsonProperty("responsavel_nome") String responsavelNome,
        String status,
        @JsonProperty("foto_url") String fotoUrl,
        @JsonProperty("criado_em") Instant criadoEm) {

    public static PrestadorAdminItemResponse from(Prestador prestador, String responsavelNome) {
        return new PrestadorAdminItemResponse(prestador.getId(), prestador.getPerfilUsuario().getKeycloakId(),
                prestador.getNomeEstab(), responsavelNome,
                prestador.getStatus().name().toLowerCase(java.util.Locale.ROOT),
                prestador.getFotoUrl(), prestador.getCriadoEm());
    }
}
