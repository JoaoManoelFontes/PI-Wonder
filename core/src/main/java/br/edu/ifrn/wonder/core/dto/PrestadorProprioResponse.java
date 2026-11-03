package br.edu.ifrn.wonder.core.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;

public record PrestadorProprioResponse(
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
        String fotoUrl,
        @JsonProperty("motivo_rejeicao") String motivoRejeicao
) {

    public static PrestadorProprioResponse from(Prestador prestador) {
        return new PrestadorProprioResponse(
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
                prestador.getFotoUrl(),
                prestador.getMotivoRejeicao()
        );
    }

    private static String formatStatus(StatusPrestador status) {
        return status == null ? null : status.name().toLowerCase();
    }
}
