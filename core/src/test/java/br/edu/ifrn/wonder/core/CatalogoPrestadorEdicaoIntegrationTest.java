package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoPrestadorEdicaoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDENTE")
    void deveAtualizarCadastroDoDonoSemAlterarPublicacao(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(situacao);
        Long prestadorId = prestador.getId();
        UUID keycloakId = prestador.getPerfilUsuario().getKeycloakId();
        UUID perfilId = prestador.getPerfilUsuario().getId();
        boolean ativo = prestador.isAtivo();
        LocalDateTime enviadoEm = prestador.getEnviadoEm();
        LocalDateTime aprovadoEm = prestador.getAprovadoEm();

        mockMvc.perform(put("/catalogo/prestadores/{id}", prestadorId)
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeEstab":"Studio atualizado","documento":"98765432100",
                                 "endereco":"Rua Nova","numero":"20","bairro":"Novo bairro",
                                 "cidade":"Parnamirim","estado":"RN","complemento":"Sala 2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(13)))
                .andExpect(jsonPath("$.id").value(prestadorId.intValue()))
                .andExpect(jsonPath("$.perfilUsuarioId").value(perfilId.toString()))
                .andExpect(jsonPath("$.nomeEstab").value("Studio atualizado"))
                .andExpect(jsonPath("$.documento").value("98765432100"))
                .andExpect(jsonPath("$.endereco").value("Rua Nova"))
                .andExpect(jsonPath("$.numero").value("20"))
                .andExpect(jsonPath("$.bairro").value("Novo bairro"))
                .andExpect(jsonPath("$.cidade").value("Parnamirim"))
                .andExpect(jsonPath("$.estado").value("RN"))
                .andExpect(jsonPath("$.complemento").value("Sala 2"))
                .andExpect(jsonPath("$.status").value(situacao.name().toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.ativo").value(ativo))
                .andExpect(jsonPath("$.fotoUrl").value("https://cdn.wonder.dev/original.jpg"));

        prestadorRepository.flush();
        entityManager.clear();
        Prestador salvo = prestadorRepository.findById(prestadorId).orElseThrow();
        assertThat(salvo.getNomeEstab()).isEqualTo("Studio atualizado");
        assertThat(salvo.getDocumento()).isEqualTo("98765432100");
        assertThat(salvo.getEndereco()).isEqualTo("Rua Nova");
        assertThat(salvo.getNumero()).isEqualTo("20");
        assertThat(salvo.getBairro()).isEqualTo("Novo bairro");
        assertThat(salvo.getCidade()).isEqualTo("Parnamirim");
        assertThat(salvo.getEstado()).isEqualTo("RN");
        assertThat(salvo.getComplemento()).isEqualTo("Sala 2");
        assertThat(salvo.getPerfilUsuario().getId()).isEqualTo(perfilId);
        assertThat(salvo.getStatus()).isEqualTo(situacao);
        assertThat(salvo.isAtivo()).isEqualTo(ativo);
        assertThat(salvo.getEnviadoEm()).isEqualTo(enviadoEm);
        assertThat(salvo.getAprovadoEm()).isEqualTo(aprovadoEm);
        assertThat(salvo.getAprovadoPor()).isEqualTo("admin-original");
        assertThat(salvo.getMotivoRejeicao()).isEqualTo("Motivo original");
    }

    @Test
    void devePreservarCamposOmitidosOuNulosNaEdicaoParcial() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        mockMvc.perform(put("/catalogo/prestadores/{id}", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cidade":"Parnamirim","nomeEstab":null,"complemento":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeEstab").value("Studio original"))
                .andExpect(jsonPath("$.documento").value("12345678900"))
                .andExpect(jsonPath("$.endereco").value("Rua original"))
                .andExpect(jsonPath("$.numero").value("10"))
                .andExpect(jsonPath("$.bairro").value("Centro"))
                .andExpect(jsonPath("$.cidade").value("Parnamirim"))
                .andExpect(jsonPath("$.estado").value("RN"))
                .andExpect(jsonPath("$.complemento").value("Sala 1"));
    }

    @Test
    void deveExigirLogin() throws Exception {
        mockMvc.perform(put("/catalogo/prestadores/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeEstab\":\"Studio alterado\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveProibirEdicaoDeOutroUsuarioMesmoComPapelAdmin() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        UUID outroUsuario = UUID.randomUUID();
        mockMvc.perform(put("/catalogo/prestadores/{id}", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(outroUsuario.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nomeEstab\":\"Nome indevido\"}"))
                .andExpect(status().isForbidden());

        assertThat(prestador.getNomeEstab()).isEqualTo("Studio original");
    }

    @Test
    void deveRetornar404ParaPrestadorInexistente() throws Exception {
        mockMvc.perform(put("/catalogo/prestadores/{id}", Long.MAX_VALUE)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nomeEstab\":\"Studio alterado\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveBloquearEdicaoDuranteAnaliseSemAlterarDados() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        mockMvc.perform(put("/catalogo/prestadores/{id}", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nomeEstab\":\"Studio alterado\"}"))
                .andExpect(status().isConflict());

        assertThat(prestador.getNomeEstab()).isEqualTo("Studio original");
        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
    }

    @ParameterizedTest
    @CsvSource({"nomeEstab,255", "documento,20", "endereco,255", "numero,30",
            "bairro,120", "cidade,120", "estado,2", "complemento,255"})
    void deveValidarLimitesDosCamposSemAlterarCadastro(String campo, int limite) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        mockMvc.perform(put("/catalogo/prestadores/{id}", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of(campo, "x".repeat(limite + 1)))))
                .andExpect(status().isBadRequest());

        assertThat(prestador.getNomeEstab()).isEqualTo("Studio original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    void deveRejeitarNomeEmBranco(String nome) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        mockMvc.perform(put("/catalogo/prestadores/{id}", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nomeEstab", nome))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void naoDeveAceitarTrocaDeProprietarioPublicacaoOuFotoPeloPayload() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.REJEITADO);
        UUID perfilId = prestador.getPerfilUsuario().getId();
        mockMvc.perform(put("/catalogo/prestadores/{id}", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeEstab":"Nome corrigido","status":"ATIVO","ativo":true,
                                 "perfilUsuarioId":"10000000-0000-0000-0000-000000000001",
                                 "aprovadoPor":"invasor","aprovadoEm":"2099-01-01T10:00:00",
                                 "motivoRejeicao":null,"fotoUrl":"https://cdn.wonder.dev/indevida.jpg"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeEstab").value("Nome corrigido"))
                .andExpect(jsonPath("$.perfilUsuarioId").value(perfilId.toString()))
                .andExpect(jsonPath("$.status").value("rejeitado"))
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.fotoUrl").value("https://cdn.wonder.dev/original.jpg"));

        assertThat(prestador.getAprovadoPor()).isEqualTo("admin-original");
        assertThat(prestador.getAprovadoEm()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
        assertThat(prestador.getMotivoRejeicao()).isEqualTo("Motivo original");
    }

    private Prestador criarPrestador(StatusPrestador situacao) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder().perfilUsuario(perfil).nomeEstab("Studio original")
                .documento("12345678900").endereco("Rua original").numero("10").bairro("Centro")
                .cidade("Natal").estado("RN").complemento("Sala 1").status(situacao)
                .ativo(situacao == StatusPrestador.ATIVO).fotoUrl("https://cdn.wonder.dev/original.jpg")
                .enviadoEm(LocalDateTime.of(2026, 1, 1, 10, 0))
                .aprovadoEm(LocalDateTime.of(2026, 1, 2, 10, 0)).aprovadoPor("admin-original")
                .motivoRejeicao("Motivo original").build());
    }
}
