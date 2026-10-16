package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;

public record PrestadorSolicitacaoDetalheResponse(
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
        LocalDateTime enviadoEm,
        String fotoUrl
) {

    public static PrestadorSolicitacaoDetalheResponse from(Prestador prestador) {
        return new PrestadorSolicitacaoDetalheResponse(
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
                prestador.getEnviadoEm(),
                prestador.getFotoUrl()
        );
    }

    private static String formatStatus(StatusPrestador status) {
        return status == null ? null : status.name().toLowerCase();
    }
}
