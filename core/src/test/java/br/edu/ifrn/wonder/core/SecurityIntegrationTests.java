package br.edu.ifrn.wonder.core;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({TestcontainersConfiguration.class, SecurityIntegrationTests.TestController.class})
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAllowActuatorHealthWithoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectProtectedEndpointWithoutJwt() throws Exception {
        mockMvc.perform(get("/test/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldExposeAuthenticatedUserIdFromJwtSubject() throws Exception {
        mockMvc.perform(get("/test/me").with(wonderJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("keycloak-user-id"))
                .andExpect(jsonPath("$.email").value("customer@wonder.dev"));
    }

    @Test
    void shouldAllowEndpointWhenUserHasRequiredRole() throws Exception {
        mockMvc.perform(get("/test/customer").with(wonderJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectEndpointWhenUserDoesNotHaveRequiredRole() throws Exception {
        mockMvc.perform(get("/test/customer").with(jwt()))
                .andExpect(status().isForbidden());
    }

    private JwtRequestPostProcessor wonderJwt() {
        return jwt()
                .jwt(jwt -> jwt
                        .subject("keycloak-user-id")
                        .claim("email", "customer@wonder.dev")
                        .claim("realm_access", Map.of("roles", List.of("CUSTOMER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/me")
        Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
            return Map.of(
                    "userId", jwt.getSubject(),
                    "email", jwt.getClaimAsString("email"));
        }

        @PreAuthorize("hasRole('CUSTOMER')")
        @GetMapping("/test/customer")
        Map<String, String> customer() {
            return Map.of("status", "ok");
        }
    }
}
