package br.edu.ifrn.wonder.core.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;

public record PerfilUsuarioResponse(
        UUID id,
        @JsonProperty("keycloak_id")
        UUID keycloakId,
        @JsonProperty("foto_url")
        String fotoUrl,
        @JsonProperty("numero_telefone")
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
