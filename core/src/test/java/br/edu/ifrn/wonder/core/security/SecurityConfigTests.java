package br.edu.ifrn.wonder.core.security;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

class SecurityConfigTests {
    @Test
    void devePreservarScopesEMapearRolesDoKeycloakSemCache() {
        String id = UUID.randomUUID().toString();
        AtomicInteger chamadas = new AtomicInteger();
        var introspector = new KeycloakTokenIntrospector(token -> {
            chamadas.incrementAndGet();
            return new DefaultOAuth2AuthenticatedPrincipal(Map.of("sub", id,
                    "realm_access", Map.of("roles", List.of("CUSTOMER", "PROVIDER", "ADMIN", 42))),
                    List.of(new SimpleGrantedAuthority("SCOPE_email")));
        });
        var principal = introspector.introspect("token");
        introspector.introspect("token");
        assertThat(chamadas.get()).isEqualTo(2);
        assertThat(principal.getName()).isEqualTo(id);
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_email", "ROLE_CUSTOMER", "ROLE_PROVIDER", "ROLE_ADMIN");
    }

    @Test
    void deveRecusarTokenInativoESubInvalidoSemConcederAcesso() {
        var inativo = new KeycloakTokenIntrospector(token -> { throw new BadOpaqueTokenException("inactive"); });
        assertThatThrownBy(() -> inativo.introspect("token")).isInstanceOf(BadOpaqueTokenException.class);
        var invalido = new KeycloakTokenIntrospector(token ->
                new DefaultOAuth2AuthenticatedPrincipal(Map.of("sub", "inválido"), List.of()));
        assertThatThrownBy(() -> invalido.introspect("token")).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void deveIgnorarRolesAusentesOuMalformadas() {
        for (Object acesso : List.of(Map.of(), Map.of("roles", "ADMIN"), "ADMIN")) {
            var introspector = new KeycloakTokenIntrospector(token -> new DefaultOAuth2AuthenticatedPrincipal(
                    Map.of("sub", UUID.randomUUID().toString(), "realm_access", acesso), List.of()));
            assertThat(introspector.introspect("token").getAuthorities()).isEmpty();
        }
    }
}
