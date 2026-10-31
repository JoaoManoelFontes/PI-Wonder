package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.Prestador;

public record PrestadorAdminResumoResponse(
        Long id,
        @JsonProperty("usuario_id") UUID usuarioId,
        @JsonProperty("nome_estab") String nomeEstab,
        String documento,
        String cidade,
        String estado,
        String status,
        @JsonProperty("foto_url") String fotoUrl,
        @JsonProperty("solicitante_foto_url") String solicitanteFotoUrl,
        @JsonProperty("enviado_em") LocalDateTime enviadoEm,
        @JsonProperty("aprovado_em") LocalDateTime aprovadoEm,
        @JsonProperty("aprovado_por") String aprovadoPor,
        @JsonProperty("motivo_rejeicao") String motivoRejeicao
) {

    public static PrestadorAdminResumoResponse from(Prestador prestador) {
        return new PrestadorAdminResumoResponse(
                prestador.getId(),
                prestador.getPerfilUsuario().getKeycloakId(),
                prestador.getNomeEstab(),
                prestador.getDocumento(),
                prestador.getCidade(),
                prestador.getEstado(),
                prestador.getStatus().name().toLowerCase(),
                prestador.getFotoUrl(),
                prestador.getPerfilUsuario().getFotoUrl(),
                prestador.getEnviadoEm(),
                prestador.getAprovadoEm(),
                prestador.getAprovadoPor(),
                prestador.getMotivoRejeicao()
        );
    }
}
