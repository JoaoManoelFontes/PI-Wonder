package br.edu.ifrn.wonder.core.dto;

import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;

public record PrestadorPublicResponse(
        Long id,
        UUID perfilUsuarioId,
        String nomeEstab,
        String documento,
        String endereco,
        String numero,
        String bairro,
        String cidade,
        String estado,
        String complemento,
        String status,
        boolean ativo,
        String fotoUrl
) {

    public static PrestadorPublicResponse from(Prestador prestador) {
        return new PrestadorPublicResponse(
                prestador.getId(),
                prestador.getPerfilUsuario().getId(),
                prestador.getNomeEstab(),
                prestador.getDocumento(),
                prestador.getEndereco(),
                prestador.getNumero(),
                prestador.getBairro(),
                prestador.getCidade(),
                prestador.getEstado(),
                prestador.getComplemento(),
                formatStatus(prestador.getStatus()),
                prestador.isAtivo(),
                prestador.getFotoUrl()
        );
    }

    private static String formatStatus(StatusPrestador status) {
        return status == null ? null : status.name().toLowerCase();
    }
}
