package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
class CatalogoPrestadorLogadoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;

    @Test
    void deveExigirLoginNaConsultaDoProprioCadastro() throws Exception {
        mockMvc.perform(get("/catalogo/prestadores/me"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(StatusPrestador.class)
    void deveConsultarSomenteCadastroDoUsuarioIndependentementeDaPublicacao(StatusPrestador situacao) throws Exception {
        UUID keycloakId = UUID.randomUUID();
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(keycloakId));
        PerfilUsuario outroPerfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        Prestador outroPrestador = prestadorRepository.save(Prestador.builder().perfilUsuario(outroPerfil)
                .nomeEstab("Outro estabelecimento").status(StatusPrestador.ATIVO).ativo(true).build());
        Prestador prestador = prestadorRepository.save(Prestador.builder().perfilUsuario(perfil)
                .nomeEstab("Meu estabelecimento").documento("12345678900").endereco("Rua das Flores")
                .numero("10").bairro("Centro").cidade("Natal").estado("RN").complemento("Sala 1")
                .fotoUrl("https://cdn.wonder.dev/meu-estabelecimento.jpg").status(situacao).ativo(false).build());

        for (boolean ativo : new boolean[] {false, true}) {
            prestador.setAtivo(ativo);
            prestadorRepository.flush();

            mockMvc.perform(get("/catalogo/prestadores/me")
                            .with(jwt().jwt(token -> token.subject(keycloakId.toString())))
                            .param("prestador_id", outroPrestador.getId().toString())
                            .param("perfilUsuarioId", outroPerfil.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", aMapWithSize(13)))
                    .andExpect(jsonPath("$.id").value(prestador.getId().intValue()))
                    .andExpect(jsonPath("$.perfilUsuarioId").value(perfil.getId().toString()))
                    .andExpect(jsonPath("$.nomeEstab").value("Meu estabelecimento"))
                    .andExpect(jsonPath("$.documento").value("12345678900"))
                    .andExpect(jsonPath("$.endereco").value("Rua das Flores"))
                    .andExpect(jsonPath("$.numero").value("10"))
                    .andExpect(jsonPath("$.bairro").value("Centro"))
                    .andExpect(jsonPath("$.cidade").value("Natal"))
                    .andExpect(jsonPath("$.estado").value("RN"))
                    .andExpect(jsonPath("$.complemento").value("Sala 1"))
                    .andExpect(jsonPath("$.status").value(situacao.name().toLowerCase(Locale.ROOT)))
                    .andExpect(jsonPath("$.ativo").value(ativo))
                    .andExpect(jsonPath("$.fotoUrl").value("https://cdn.wonder.dev/meu-estabelecimento.jpg"));
        }
    }

    @Test
    void deveRetornar404QuandoPerfilNaoPossuiCadastroDePrestador() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        perfilUsuarioRepository.save(new PerfilUsuario(keycloakId));

        mockMvc.perform(get("/catalogo/prestadores/me")
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar404SemCriarPerfilQuandoUsuarioAindaNaoPossuiPerfilLocal() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        long quantidadePerfis = perfilUsuarioRepository.count();

        mockMvc.perform(get("/catalogo/prestadores/me")
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString()))))
                .andExpect(status().isNotFound());

        assertThat(perfilUsuarioRepository.findByKeycloakId(keycloakId)).isEmpty();
        assertThat(perfilUsuarioRepository.count()).isEqualTo(quantidadePerfis);
    }

    @Test
    void deveRejeitarIdentificadorInvalidoNoJwt() throws Exception {
        mockMvc.perform(get("/catalogo/prestadores/me")
                        .with(jwt().jwt(token -> token.subject("identificador-invalido"))))
                .andExpect(status().isUnauthorized());
    }
}
