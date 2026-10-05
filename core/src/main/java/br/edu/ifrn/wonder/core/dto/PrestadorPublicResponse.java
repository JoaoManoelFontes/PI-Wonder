package br.edu.ifrn.wonder.core.dto;

import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PrestadorPublicResponse(
        Long id,
        @JsonProperty("perfil_usuario_id")
        UUID perfilUsuarioId,
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
                prestador.getFotoUrl()
        );
    }

    private static String formatStatus(StatusPrestador status) {
        return status == null ? null : status.name().toLowerCase();
    }
}
