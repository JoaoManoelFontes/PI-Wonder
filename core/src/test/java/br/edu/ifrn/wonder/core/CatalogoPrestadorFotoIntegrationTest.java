package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import jakarta.persistence.EntityManager;

@Testcontainers
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoPrestadorFotoIntegrationTest {

    private static final String BUCKET = "wonder-fotos-prestadores-teste";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6l8AAAAASUVORK5CYII=");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("bitnamilegacy/minio:2025.7.23"))
            .withEnv("MINIO_ROOT_USER", "wonder")
            .withEnv("MINIO_ROOT_PASSWORD", "wonder-secret")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void configurarMinio(DynamicPropertyRegistry registry) {
        registry.add("wonder.minio.endpoint", CatalogoPrestadorFotoIntegrationTest::endpointMinio);
        registry.add("wonder.minio.access-key", () -> "wonder");
        registry.add("wonder.minio.secret-key", () -> "wonder-secret");
        registry.add("wonder.minio.bucket", () -> BUCKET);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void deveArmazenarSubstituirEServirFotoPublicadaSemAlterarPerfilOuPublicacao() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Long id = prestador.getId();
        UUID dono = prestador.getPerfilUsuario().getKeycloakId();
        UUID perfilId = prestador.getPerfilUsuario().getId();
        LocalDateTime aprovadoEm = prestador.getAprovadoEm();

        mockMvc.perform(multipart(rota(id)).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(13)))
                .andExpect(jsonPath("$.id").value(id.intValue()))
                .andExpect(jsonPath("$.fotoUrl").value(url(id)));

        prestadorRepository.flush();
        entityManager.clear();
        Prestador salvo = prestadorRepository.findById(id).orElseThrow();
        assertThat(salvo.getFotoUrl()).isEqualTo(url(id));
        assertThat(salvo.getStatus()).isEqualTo(StatusPrestador.ATIVO);
        assertThat(salvo.isAtivo()).isTrue();
        assertThat(salvo.getAprovadoEm()).isEqualTo(aprovadoEm);
        assertThat(salvo.getAprovadoPor()).isEqualTo("admin-original");
        assertThat(salvo.getMotivoRejeicao()).isEqualTo("Motivo original");
        assertThat(salvo.getNomeEstab()).isEqualTo("Studio foto");
        assertThat(salvo.getPerfilUsuario().getId()).isEqualTo(perfilId);
        assertThat(salvo.getPerfilUsuario().getFotoUrl()).isEqualTo("foto-do-perfil");

        mockMvc.perform(get(rota(id)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().longValue("Content-Length", PNG.length))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/catalogo/prestadores/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fotoUrl").value(url(id)));
        mockMvc.perform(get("/catalogo/prestadores/me")
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fotoUrl").value(url(id)));
        mockMvc.perform(get("/catalogo/prestadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)].fotoUrl".formatted(id))
                        .value(org.hamcrest.Matchers.contains(url(id))));

        byte[] novaFoto = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        mockMvc.perform(multipart(rota(id)).file(foto("image/jpeg", novaFoto))
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fotoUrl").value(url(id)));
        mockMvc.perform(get(rota(id)))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(novaFoto));

        MinioClient minio = MinioClient.builder().endpoint(endpointMinio())
                .credentials("wonder", "wonder-secret").build();
        String objeto = "prestadores/%s/foto".formatted(id);
        assertThat(minio.statObject(StatObjectArgs.builder().bucket(BUCKET).object(objeto).build()).size())
                .isEqualTo(novaFoto.length);
        int quantidade = 0;
        for (var item : minio.listObjects(ListObjectsArgs.builder().bucket(BUCKET)
                .prefix("prestadores/%s/".formatted(id)).recursive(true).build())) {
            assertThat(item.get().objectName()).isEqualTo(objeto);
            quantidade++;
        }
        assertThat(quantidade).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDENTE")
    void devePermitirUploadDoDonoNosEstadosEditaveis(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(situacao);
        mockMvc.perform(multipart(rota(prestador.getId())).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(situacao.name().toLowerCase(java.util.Locale.ROOT)))
                .andExpect(jsonPath("$.ativo").value(prestador.isAtivo()));
    }

    @ParameterizedTest
    @EnumSource(StatusPrestador.class)
    void deveProtegerFotoNaoPublicadaPermitindoDonoEAdministrador(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        Long id = prestador.getId();
        UUID dono = prestador.getPerfilUsuario().getKeycloakId();
        mockMvc.perform(multipart(rota(id)).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(status().isOk());
        prestador.setStatus(situacao);
        prestador.setAtivo(false);
        prestadorRepository.flush();

        mockMvc.perform(get(rota(id))).andExpect(status().isNotFound());
        mockMvc.perform(get(rota(id)).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(rota(id)).with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG));
        mockMvc.perform(get(rota(id)).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG));
        // Mesmo com ativo=true, apenas status ATIVO pode publicar a imagem.
        prestador.setAtivo(true);
        prestadorRepository.flush();
        mockMvc.perform(get(rota(id)))
                .andExpect(situacao == StatusPrestador.ATIVO ? status().isOk() : status().isNotFound());
    }

    @Test
    void deveExigirLoginParaUpload() throws Exception {
        mockMvc.perform(multipart(rota(1L)).file(foto("image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_ADMIN"})
    void deveImpedirUploadPorOutroUsuarioInclusiveAdministrador(String papel) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        mockMvc.perform(multipart(rota(prestador.getId())).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority(papel))))
                .andExpect(status().isForbidden());
        assertThat(prestador.getFotoUrl()).isNull();
    }

    @Test
    void deveBloquearUploadDuranteAnalise() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        mockMvc.perform(multipart(rota(prestador.getId())).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isConflict());
        assertThat(prestador.getFotoUrl()).isNull();
    }

    @Test
    void deveRetornar404ParaPrestadorInexistente() throws Exception {
        mockMvc.perform(multipart(rota(Long.MAX_VALUE)).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(rota(Long.MAX_VALUE))).andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar404SemFotoOuComUrlExternaAnterior() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        mockMvc.perform(get(rota(prestador.getId()))).andExpect(status().isNotFound());
        prestador.setFotoUrl("https://cdn.wonder.dev/foto-anterior.png");
        mockMvc.perform(get(rota(prestador.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/catalogo/prestadores/{id}", prestador.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value("https://cdn.wonder.dev/foto-anterior.png"));
    }

    @ParameterizedTest
    @MethodSource("arquivosInvalidos")
    void deveRejeitarArquivoInvalidoPreservandoFotoAnterior(String tipo, byte[] bytes, int codigo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        prestador.setFotoUrl("https://cdn.wonder.dev/original.png");
        mockMvc.perform(multipart(rota(prestador.getId())).file(foto(tipo, bytes))
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().is(codigo));
        prestadorRepository.flush();
        entityManager.clear();
        assertThat(prestadorRepository.findById(prestador.getId()).orElseThrow().getFotoUrl())
                .isEqualTo("https://cdn.wonder.dev/original.png");
    }

    static Stream<Arguments> arquivosInvalidos() {
        return Stream.of(
                Arguments.of("image/png", new byte[0], 400),
                Arguments.of("text/plain", PNG, 400),
                Arguments.of(null, PNG, 400),
                Arguments.of("image/png", "texto".getBytes(StandardCharsets.UTF_8), 400),
                Arguments.of("image/jpeg", PNG, 400),
                Arguments.of("image/webp", "RIFF0000WAVE".getBytes(StandardCharsets.US_ASCII), 400),
                Arguments.of("image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47}, 400),
                Arguments.of("image/png", new byte[5 * 1024 * 1024 + 1], 413));
    }

    @Test
    void deveExigirParteFileEMultipart() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        UUID dono = prestador.getPerfilUsuario().getKeycloakId();
        mockMvc.perform(multipart(rota(prestador.getId()))
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(rota(prestador.getId())).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void deveAceitarWebpELimiteExatoDeCincoMb() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        byte[] webp = new byte[5 * 1024 * 1024];
        byte[] assinatura = "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(assinatura, 0, webp, 0, assinatura.length);
        mockMvc.perform(multipart(rota(prestador.getId())).file(foto("image/webp", webp))
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isOk());
        mockMvc.perform(get(rota(prestador.getId())))
                .andExpect(status().isOk()).andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(webp));
    }

    private Prestador criarPrestador(StatusPrestador situacao) {
        PerfilUsuario perfil = new PerfilUsuario(UUID.randomUUID());
        perfil.setFotoUrl("foto-do-perfil");
        perfilUsuarioRepository.saveAndFlush(perfil);
        return prestadorRepository.saveAndFlush(Prestador.builder()
                .perfilUsuario(perfil).nomeEstab("Studio foto").documento("12345678900")
                .status(situacao).ativo(situacao == StatusPrestador.ATIVO)
                .aprovadoEm(LocalDateTime.of(2026, 1, 1, 12, 0))
                .aprovadoPor("admin-original").motivoRejeicao("Motivo original").build());
    }

    private MockMultipartFile foto(String tipo, byte[] bytes) {
        return new MockMultipartFile("file", "foto", tipo, bytes);
    }

    private String rota(Long id) {
        return "/catalogo/prestadores/%s/foto".formatted(id);
    }

    private String url(Long id) {
        return "http://api.wonder.test" + rota(id);
    }

    private static String endpointMinio() {
        return "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000));
    }
}
