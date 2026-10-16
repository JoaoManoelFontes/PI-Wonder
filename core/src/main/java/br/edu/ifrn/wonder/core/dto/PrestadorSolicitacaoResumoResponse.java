package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;

public record PrestadorSolicitacaoResumoResponse(
        Long id,
        UUID perfilUsuarioId,
        String nomeEstab,
        String documento,
        String cidade,
        String estado,
        String status,
        LocalDateTime enviadoEm,
        String fotoUrl
) {

    public static PrestadorSolicitacaoResumoResponse from(Prestador prestador) {
        return new PrestadorSolicitacaoResumoResponse(
                prestador.getId(),
                prestador.getPerfilUsuario().getId(),
                prestador.getNomeEstab(),
                prestador.getDocumento(),
                prestador.getCidade(),
                prestador.getEstado(),
                formatStatus(prestador.getStatus()),
                prestador.getEnviadoEm(),
                prestador.getFotoUrl()
        );
    }

    private static String formatStatus(StatusPrestador status) {
        return status == null ? null : status.name().toLowerCase();
    }
}
