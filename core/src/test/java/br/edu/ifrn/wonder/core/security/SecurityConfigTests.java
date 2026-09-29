package br.edu.ifrn.wonder.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTests {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void shouldConvertKeycloakRealmRolesToSpringAuthorities() {
        Jwt jwt = jwt(Map.of(
                "sub", "keycloak-user-id",
                "realm_access", Map.of("roles", List.of("CUSTOMER", "PROVIDER"))));

        AbstractAuthenticationToken authentication = securityConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("keycloak-user-id");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_CUSTOMER", "ROLE_PROVIDER");
    }

    @Test
    void shouldIgnoreRealmRolesWhenClaimIsMissing() {
        Jwt jwt = jwt(Map.of("sub", "keycloak-user-id"));

        AbstractAuthenticationToken authentication = securityConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .doesNotContain("ROLE_CUSTOMER", "ROLE_PROVIDER", "ROLE_ADMIN");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                claims);
    }
}
