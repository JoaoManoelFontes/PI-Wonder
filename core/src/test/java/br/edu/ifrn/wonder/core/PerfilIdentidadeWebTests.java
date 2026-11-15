package br.edu.ifrn.wonder.core;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import br.edu.ifrn.wonder.core.client.KeycloakClient;
import br.edu.ifrn.wonder.core.controller.PerfilIdentidadeController;
import br.edu.ifrn.wonder.core.security.SecurityConfig;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.UsuarioAdminService;

@WebMvcTest(PerfilIdentidadeController.class)
@Import({SecurityConfig.class, UsuarioAutenticadoProvider.class, UsuarioAdminService.class})
class PerfilIdentidadeWebTests {
    @Autowired MockMvc mvc;
    @MockitoBean KeycloakClient cliente;
    @MockitoBean OpaqueTokenIntrospector introspector;
    private final UUID id = UUID.randomUUID();
    private final String pedido = "{\"nome\":\" Ana \",\"sobrenome\":\" Silva \",\"email\":\" ana@example.com \"}";

    @BeforeEach
    void preparar() {
        when(cliente.obterToken()).thenReturn("token-servico");
        when(cliente.buscarUsuario("token-servico", id)).thenReturn(Map.of(
                "id", id.toString(), "username", "login-original", "firstName", "Antes",
                "email", "antes@example.com", "emailVerified", true, "enabled", true,
                "attributes", Map.of("cidade", java.util.List.of("Natal"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "PROVIDER", "ADMIN"})
    void deveEditarSomenteContaDaSessaoPreservandoOutrosAtributos(String role) throws Exception {
        mvc.perform(put("/perfis/eu/identidade").with(opaqueToken().attributes(a -> a.put("sub", id.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                        .contentType(MediaType.APPLICATION_JSON).content(pedido))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.ativo").doesNotExist());
        verify(cliente).atualizarUsuario(eq("token-servico"), eq(id), argThat(u ->
                "Ana".equals(u.get("firstName")) && "Silva".equals(u.get("lastName")) &&
                "ana@example.com".equals(u.get("email")) && Boolean.FALSE.equals(u.get("emailVerified")) &&
                Boolean.TRUE.equals(u.get("enabled")) && "login-original".equals(u.get("username")) &&
                Map.of("cidade", java.util.List.of("Natal")).equals(u.get("attributes"))));
    }

    @Test
    void deveConsultarIdentidadeAtualSemDependerDosDadosAntigosDoToken() throws Exception {
        mvc.perform(get("/perfis/eu/identidade").with(opaqueToken().attributes(a -> {
                    a.put("sub", id.toString()); a.put("email", "token-antigo@example.com");
                }).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("antes@example.com"));
    }

    @Test
    void deveRecusarAnonimoOuSemPapelReconhecido() throws Exception {
        mvc.perform(get("/perfis/eu/identidade")).andExpect(status().isUnauthorized());
        mvc.perform(put("/perfis/eu/identidade").contentType(MediaType.APPLICATION_JSON).content(pedido))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/perfis/eu/identidade").with(opaqueToken()).contentType(MediaType.APPLICATION_JSON).content(pedido))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cliente);
    }

    @Test
    void deveValidarNomeEmailESobrenomeAntesDeChamarIdentidade() throws Exception {
        for (String invalido : java.util.List.of("{}", "{\"nome\":\" \",\"email\":\"x@y.com\"}",
                "{\"nome\":\"Ana\",\"email\":\"invalido\"}",
                "{\"nome\":\"Ana\",\"email\":\"x@y.com\",\"sobrenome\":\"" + "a".repeat(256) + "\"}")) {
            mvc.perform(put("/perfis/eu/identidade").with(opaqueToken().attributes(a -> a.put("sub", id.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                    .contentType(MediaType.APPLICATION_JSON).content(invalido)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cliente);
    }

    @Test
    void deveRetornarConflitoEIndisponibilidadeSemExibirSucesso() throws Exception {
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY,
                new byte[0], StandardCharsets.UTF_8)).when(cliente).atualizarUsuario(anyString(), any(), any());
        mvc.perform(put("/perfis/eu/identidade").with(opaqueToken().attributes(a -> a.put("sub", id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_PROVIDER")))
                .contentType(MediaType.APPLICATION_JSON).content(pedido)).andExpect(status().isConflict());
        when(cliente.obterToken()).thenThrow(new ResourceAccessException("indisponivel"));
        mvc.perform(get("/perfis/eu/identidade").with(opaqueToken().attributes(a -> a.put("sub", id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_PROVIDER")))).andExpect(status().isServiceUnavailable());
    }
}
