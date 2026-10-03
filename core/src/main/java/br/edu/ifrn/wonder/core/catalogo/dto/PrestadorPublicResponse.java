package br.edu.ifrn.wonder.core.catalogo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.catalogo.domain.Prestador;
import br.edu.ifrn.wonder.core.catalogo.domain.StatusPrestador;

public record PrestadorPublicResponse(
        Long id,
        @JsonProperty("usuario_id")
        Long usuarioId,
        @JsonProperty("nome_estab")
        String nomeEstab,
        String documento,
        String endereco,
        String numero,
        String bairro,
        String cidade,
        String estado,
        String complemento,
        String status,
        @JsonProperty("foto_url")
        String fotoUrl
) {

    public static PrestadorPublicResponse from(Prestador prestador) {
        return new PrestadorPublicResponse(
                prestador.getId(),
                prestador.getUsuarioId(),
                prestador.getNomeEstab(),
                prestador.getDocumento(),
                prestador.getEndereco(),
                prestador.getNumero(),
                prestador.getBairro(),
                prestador.getCidade(),
                prestador.getEstado(),
                prestador.getComplemento(),
                formatStatus(prestador.getStatus()),
                prestador.getFotoUrl()
        );
    }

    private static String formatStatus(StatusPrestador status) {
        return status == null ? null : status.name().toLowerCase();
    }
}
