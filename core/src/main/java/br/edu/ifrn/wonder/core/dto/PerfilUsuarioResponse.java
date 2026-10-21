package br.edu.ifrn.wonder.core.dto;

import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;

public record PerfilUsuarioResponse(
        UUID id,
        UUID keycloakId,
        String fotoUrl,
        String numeroTelefone
) {

    public static PerfilUsuarioResponse from(PerfilUsuario perfilUsuario, String apiPublicUrl) {
        return new PerfilUsuarioResponse(
                perfilUsuario.getId(),
                perfilUsuario.getKeycloakId(),
                montarFotoUrl(perfilUsuario, apiPublicUrl),
                perfilUsuario.getNumeroTelefone()
        );
    }

    private static String montarFotoUrl(PerfilUsuario perfilUsuario, String apiPublicUrl) {
        if (perfilUsuario.getFotoUrl() == null) {
            return null;
        }

        String baseUrl = apiPublicUrl.endsWith("/")
                ? apiPublicUrl.substring(0, apiPublicUrl.length() - 1)
                : apiPublicUrl;

        return "%s/perfis/%s/foto".formatted(baseUrl, perfilUsuario.getId());
    }
}
