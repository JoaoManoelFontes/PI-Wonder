package br.edu.ifrn.wonder.core.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/** Consulta o Keycloak em cada requisição, inclusive para tokens JWT já emitidos. */
public class KeycloakTokenIntrospector implements OpaqueTokenIntrospector {

    private final OpaqueTokenIntrospector delegado;

    public KeycloakTokenIntrospector(OpaqueTokenIntrospector delegado) {
        this.delegado = delegado;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2AuthenticatedPrincipal principal = delegado.introspect(token);
        String usuarioId = principal.getAttribute("sub");
        try {
            UUID.fromString(usuarioId);
        } catch (IllegalArgumentException | NullPointerException excecao) {
            throw new BadOpaqueTokenException("Identificador do usuário inválido");
        }
        Collection<GrantedAuthority> autoridades = new ArrayList<>(principal.getAuthorities());
        Object acesso = principal.getAttribute("realm_access");
        if (acesso instanceof Map<?, ?> mapa && mapa.get("roles") instanceof Collection<?> roles) {
            roles.stream().filter(String.class::isInstance).map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).forEach(autoridades::add);
        }
        return new DefaultOAuth2AuthenticatedPrincipal(usuarioId, principal.getAttributes(), autoridades);
    }
}
