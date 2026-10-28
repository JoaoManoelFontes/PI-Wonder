package br.edu.ifrn.wonder.core;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PerfilUsuarioFotoIntegrationTests {

    private static final String BUCKET = "wonder-fotos-usuarios";
    private static final String MINIO_USUARIO = "wonder";
    private static final String MINIO_SENHA = "wonder-secret";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("bitnamilegacy/minio:2025.7.23")
    )
            .withEnv("MINIO_ROOT_USER", MINIO_USUARIO)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SENHA)
            // Evita que o health check encontre o servidor temporário do setup, que é reiniciado.
            .withEnv("MINIO_SKIP_CLIENT", "yes")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("wonder.minio.endpoint", PerfilUsuarioFotoIntegrationTests::minioEndpoint);
        registry.add("wonder.minio.access-key", () -> MINIO_USUARIO);
        registry.add("wonder.minio.secret-key", () -> MINIO_SENHA);
        registry.add("wonder.minio.bucket", () -> BUCKET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Test
    void deveEnviarBaixarESubstituirFotoMantendoSomenteObjetoNoBanco() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        byte[] primeiraFoto = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 1};

        String respostaUpload = mockMvc.perform(multipart(HttpMethod.PUT, "/perfis/eu")
                        .file(foto(primeiraFoto))
                        .param("numeroTelefone", "84999999999")
                        .with(wonderJwt(keycloakId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        PerfilUsuario perfil = perfilUsuarioRepository.findByKeycloakId(keycloakId).orElseThrow();
        String nomeObjeto = "perfis/%s/foto".formatted(perfil.getId());
        String fotoUrlPublica = "http://api.wonder.test/perfis/%s/foto".formatted(perfil.getId());

        assertThat(perfil.getFotoUrl()).isEqualTo(nomeObjeto);
        assertThat(perfil.getNumeroTelefone()).isEqualTo("84999999999");
        assertThat(respostaUpload).contains("\"fotoUrl\":\"" + fotoUrlPublica + "\"");
        assertThat(respostaUpload).contains("\"numeroTelefone\":\"84999999999\"");

        MinioClient clienteMinio = MinioClient.builder()
                .endpoint(minioEndpoint())
                .credentials(MINIO_USUARIO, MINIO_SENHA)
                .build();
        assertThat(clienteMinio.statObject(StatObjectArgs.builder()
                .bucket(BUCKET)
                .object(nomeObjeto)
                .build()).contentType()).isEqualTo("image/png");

        mockMvc.perform(get("/perfis/{perfilId}/foto", perfil.getId()).with(wonderJwt(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(primeiraFoto));

        byte[] novaFoto = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 2, 3};
        mockMvc.perform(multipart(HttpMethod.PUT, "/perfis/eu")
                        .file(foto(novaFoto))
                        .with(wonderJwt(keycloakId)))
                .andExpect(status().isOk());

        assertThat(perfilUsuarioRepository.findByKeycloakId(keycloakId).orElseThrow().getFotoUrl())
                .isEqualTo(nomeObjeto);
        assertThat(perfilUsuarioRepository.findByKeycloakId(keycloakId).orElseThrow().getNumeroTelefone())
                .isEqualTo("84999999999");
        mockMvc.perform(get("/perfis/{perfilId}/foto", perfil.getId()).with(wonderJwt(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(novaFoto));
    }

    @Test
    void deveRetornarNotFoundQuandoPerfilNaoPossuiFoto() throws Exception {
        mockMvc.perform(get("/perfis/{perfilId}/foto", UUID.randomUUID()).with(wonderJwt(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveExigirAutenticacaoParaEnviarFoto() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/perfis/eu").file(foto(new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveAtualizarSomenteTelefoneQuandoFotoNaoForEnviada() throws Exception {
        UUID keycloakId = UUID.randomUUID();

        mockMvc.perform(multipart(HttpMethod.PUT, "/perfis/eu")
                        .param("numeroTelefone", "84988888888")
                        .with(wonderJwt(keycloakId)))
                .andExpect(status().isOk());

        PerfilUsuario perfil = perfilUsuarioRepository.findByKeycloakId(keycloakId).orElseThrow();
        assertThat(perfil.getNumeroTelefone()).isEqualTo("84988888888");
        assertThat(perfil.getFotoUrl()).isNull();
    }

    private MockMultipartFile foto(byte[] conteudo) {
        return new MockMultipartFile("arquivo", "foto.png", "image/png", conteudo);
    }

    private static String minioEndpoint() {
        return "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000));
    }

    private JwtRequestPostProcessor wonderJwt(UUID keycloakId) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(keycloakId.toString())
                        .claim("email", "customer@wonder.dev")
                        .claim("realm_access", Map.of("roles", List.of("CUSTOMER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
