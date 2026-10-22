package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CadastroPrestadorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;

    @Test
    void deveCadastrarNaRotaDoCatalogoVinculandoAoUsuarioAutenticadoComoPendente() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        PerfilUsuario outroPerfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));

        mockMvc.perform(post("/catalogo/prestadores")
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeEstab":"Studio cadastro","documento":"12345678900",
                                 "endereco":"Rua das Flores","numero":"10","bairro":"Centro",
                                 "cidade":"Natal","estado":"RN","complemento":"Sala 1",
                                 "fotoUrl":null,"status":"ATIVO","ativo":true,
                                 "perfilUsuarioId":"%s"}
                                """.formatted(outroPerfil.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(13)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nomeEstab").value("Studio cadastro"))
                .andExpect(jsonPath("$.documento").value("12345678900"))
                .andExpect(jsonPath("$.endereco").value("Rua das Flores"))
                .andExpect(jsonPath("$.numero").value("10"))
                .andExpect(jsonPath("$.bairro").value("Centro"))
                .andExpect(jsonPath("$.cidade").value("Natal"))
                .andExpect(jsonPath("$.estado").value("RN"))
                .andExpect(jsonPath("$.complemento").value("Sala 1"))
                .andExpect(jsonPath("$.status").value("pendente"))
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.fotoUrl").isEmpty());

        PerfilUsuario perfil = perfilUsuarioRepository.findByKeycloakId(keycloakId).orElseThrow();
        Prestador prestador = prestadorRepository.findByPerfilUsuarioId(perfil.getId()).orElseThrow();
        assertThat(prestador.getPerfilUsuario().getId()).isNotEqualTo(outroPerfil.getId());
        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
        assertThat(prestador.isAtivo()).isFalse();
        assertThat(prestador.getEnviadoEm()).isNotNull();
        assertThat(prestador.getAprovadoEm()).isNull();
        assertThat(prestador.getAprovadoPor()).isNull();

        mockMvc.perform(get("/catalogo/prestadores/me")
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$.perfilUsuarioId").value(perfil.getId().toString()));
        mockMvc.perform(get("/catalogo/prestadores/{id}", prestador.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveExigirAutenticacaoNoCadastroDoCatalogo() throws Exception {
        mockMvc.perform(post("/catalogo/prestadores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeEstab\":\"Studio cadastro\"}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nomeEstab\":\"   \"}"})
    void deveValidarCadastroAntesDeCriarPerfil(String corpo) throws Exception {
        UUID keycloakId = UUID.randomUUID();
        mockMvc.perform(post("/catalogo/prestadores")
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest());
        assertThat(perfilUsuarioRepository.findByKeycloakId(keycloakId)).isEmpty();
    }

    @Test
    void deveRetornar409ParaSegundoCadastroDoMesmoUsuario() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        long quantidadeAntes = prestadorRepository.count();
        mockMvc.perform(post("/catalogo/prestadores")
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nomeEstab\":\"Studio cadastro\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/catalogo/prestadores")
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nomeEstab\":\"Segundo cadastro\"}"))
                .andExpect(status().isConflict());
        assertThat(prestadorRepository.count()).isEqualTo(quantidadeAntes + 1);
    }

    @Test
    void rotaAntigaNaoDeveCriarCadastro() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        mockMvc.perform(post("/prestadores")
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nomeEstab\":\"Studio cadastro\"}"))
                .andExpect(status().isNotFound());
        assertThat(perfilUsuarioRepository.findByKeycloakId(keycloakId)).isEmpty();
    }
}
