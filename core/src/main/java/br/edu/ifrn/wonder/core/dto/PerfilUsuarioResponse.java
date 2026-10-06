package br.edu.ifrn.wonder.core.dto;

import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;

public record PerfilUsuarioResponse(
        UUID id,
        UUID keycloakId,
        String fotoUrl,
        String numeroTelefone
) {

    public static PerfilUsuarioResponse from(PerfilUsuario perfilUsuario) {
        return new PerfilUsuarioResponse(
                perfilUsuario.getId(),
                perfilUsuario.getKeycloakId(),
                perfilUsuario.getFotoUrl(),
                perfilUsuario.getNumeroTelefone()
        );
    }
}
