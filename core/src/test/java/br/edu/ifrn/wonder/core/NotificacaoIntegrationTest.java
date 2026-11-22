package br.edu.ifrn.wonder.core;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificacaoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveListarSomenteAvisosDoUsuarioEmOrdemRecente() throws Exception {
        UUID usuario = UUID.randomUUID();
        inserir(usuario, "primeiro", "PENDENTE", Instant.parse("2026-11-15T10:00:00Z"));
        inserir(usuario, "segundo", "LIDA", Instant.parse("2026-11-15T11:00:00Z"));
        inserir(UUID.randomUUID(), "de outro usuário", "PENDENTE", Instant.parse("2026-11-15T12:00:00Z"));

        mockMvc.perform(get("/notificacoes").with(jwt().jwt(token -> token.subject(usuario.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mensagem").value("segundo"))
                .andExpect(jsonPath("$[0].usuario_id").value(usuario.toString()))
                .andExpect(jsonPath("$[0].status").value("lida"))
                .andExpect(jsonPath("$[1].mensagem").value("primeiro"));
    }

    @Test
    void deveFiltrarPendentesEMarcarAvisoComoLido() throws Exception {
        UUID usuario = UUID.randomUUID();
        Long pendente = inserir(usuario, "aviso pendente", "PENDENTE", Instant.now());
        inserir(usuario, "aviso lido", "LIDA", Instant.now().minusSeconds(60));

        mockMvc.perform(get("/notificacoes").param("status", "pendente")
                        .with(jwt().jwt(token -> token.subject(usuario.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(pendente))
                .andExpect(jsonPath("$[0].status").value("pendente"));

        mockMvc.perform(patch("/notificacoes/{id}/lida", pendente)
                        .with(jwt().jwt(token -> token.subject(usuario.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("lida"));
    }

    @Test
    void deveProtegerAvisosDeOutroUsuario() throws Exception {
        Long id = inserir(UUID.randomUUID(), "privado", "PENDENTE", Instant.now());

        mockMvc.perform(patch("/notificacoes/{id}/lida", id)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void devePaginarAvisosDoUsuarioEValidarParametros() throws Exception {
        UUID usuario = UUID.randomUUID();
        inserir(usuario, "antigo", "LIDA", Instant.parse("2026-11-15T10:00:00Z"));
        inserir(usuario, "recente", "PENDENTE", Instant.parse("2026-11-15T11:00:00Z"));
        inserir(UUID.randomUUID(), "privado", "PENDENTE", Instant.parse("2026-11-15T12:00:00Z"));

        mockMvc.perform(get("/notificacoes/pagina").param("pagina", "0").param("limite", "1")
                        .with(jwt().jwt(token -> token.subject(usuario.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.temProxima").value(true))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].mensagem").value("recente"));

        mockMvc.perform(get("/notificacoes/pagina").param("pagina", "1").param("limite", "1")
                        .with(jwt().jwt(token -> token.subject(usuario.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temProxima").value(false))
                .andExpect(jsonPath("$.itens[0].mensagem").value("antigo"));

        mockMvc.perform(get("/notificacoes/pagina").param("status", "pendente")
                        .with(jwt().jwt(token -> token.subject(usuario.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/notificacoes/pagina").param("limite", "51")
                        .with(jwt().jwt(token -> token.subject(usuario.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveMarcarTodosOsAvisosDoUsuarioSemAlterarOsDeOutro() throws Exception {
        UUID usuario = UUID.randomUUID();
        UUID outroUsuario = UUID.randomUUID();
        inserir(usuario, "primeiro", "PENDENTE", Instant.now());
        inserir(usuario, "segundo", "PENDENTE", Instant.now().minusSeconds(60));
        inserir(outroUsuario, "privado", "PENDENTE", Instant.now());

        mockMvc.perform(patch("/notificacoes/lidas")
                        .with(jwt().jwt(token -> token.subject(usuario.toString()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/notificacoes/pagina").param("status", "pendente")
                        .with(jwt().jwt(token -> token.subject(usuario.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        Integer pendentes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notificacoes WHERE usuario_keycloak_id = ? AND status = 'PENDENTE'",
                Integer.class, outroUsuario);
        org.assertj.core.api.Assertions.assertThat(pendentes).isEqualTo(1);
    }

    @Test
    void deveValidarAutenticacaoEStatus() throws Exception {
        mockMvc.perform(get("/notificacoes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notificacoes").param("status", "invalido")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());
    }

    private Long inserir(UUID usuario, String mensagem, String status, Instant criadoEm) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notificacoes
                    (usuario_keycloak_id, tipo, mensagem, status, chave_evento, criado_em)
                VALUES (?, 'teste', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, usuario, mensagem, status, UUID.randomUUID().toString(),
                Timestamp.from(criadoEm));
    }
}
