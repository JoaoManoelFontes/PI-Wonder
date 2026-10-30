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
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import jakarta.persistence.EntityManager;

@Testcontainers
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCategoriaFotoIntegrationTest {

    private static final String BUCKET = "wonder-fotos-categorias-teste";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6l8AAAAASUVORK5CYII=");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("bitnamilegacy/minio:2025.7.23"))
            .withEnv("MINIO_ROOT_USER", "wonder")
            .withEnv("MINIO_ROOT_PASSWORD", "wonder-secret")
            .withEnv("MINIO_SKIP_CLIENT", "yes")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void configurarMinio(DynamicPropertyRegistry registry) {
        registry.add("wonder.minio.endpoint", AdminCategoriaFotoIntegrationTest::endpointMinio);
        registry.add("wonder.minio.access-key", () -> "wonder");
        registry.add("wonder.minio.secret-key", () -> "wonder-secret");
        registry.add("wonder.minio.bucket", () -> BUCKET);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void deveEnviarPelasDuasRotasSubstituirEServirFotoPreservandoCategoria() throws Exception {
        String nome = nomeUnico();
        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder()
                .nome(nome).descricao("Produção para eventos").status(StatusCategoria.INATIVA).build());
        Long id = categoria.getId();

        mockMvc.perform(multipart(rotaDefinitiva(id)).file(foto("image/png", PNG)).with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.id").value(id.intValue()))
                .andExpect(jsonPath("$.nome").value(nome))
                .andExpect(jsonPath("$.descricao").value("Produção para eventos"))
                .andExpect(jsonPath("$.status").value("inativa"))
                .andExpect(jsonPath("$.foto_url").value(url(id)));

        mockMvc.perform(get(rotaLeitura(id)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().longValue("Content-Length", PNG.length))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));

        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        mockMvc.perform(multipart(rotaTemporaria(id)).file(foto("image/jpeg", jpeg)).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto_url").value(url(id)));
        mockMvc.perform(get(rotaLeitura(id)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(jpeg));

        categoriaRepository.flush();
        entityManager.clear();
        Categoria salva = categoriaRepository.findById(id).orElseThrow();
        assertThat(salva.getNome()).isEqualTo(nome);
        assertThat(salva.getDescricao()).isEqualTo("Produção para eventos");
        assertThat(salva.getStatus()).isEqualTo(StatusCategoria.INATIVA);
        assertThat(salva.getFotoUrl()).isEqualTo(url(id));

        MinioClient minio = MinioClient.builder().endpoint(endpointMinio())
                .credentials("wonder", "wonder-secret").build();
        String objeto = "categorias/%s/foto".formatted(id);
        assertThat(minio.statObject(StatObjectArgs.builder().bucket(BUCKET).object(objeto).build()).size())
                .isEqualTo(jpeg.length);
        int quantidade = 0;
        for (var item : minio.listObjects(ListObjectsArgs.builder().bucket(BUCKET)
                .prefix("categorias/%s/".formatted(id)).recursive(true).build())) {
            assertThat(item.get().objectName()).isEqualTo(objeto);
            quantidade++;
        }
        assertThat(quantidade).isEqualTo(1);
    }

    @Test
    void deveExigirAdministradorNasDuasRotasDeUpload() throws Exception {
        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder().nome(nomeUnico()).build());
        for (String rota : List.of(rotaDefinitiva(categoria.getId()), rotaTemporaria(categoria.getId()))) {
            mockMvc.perform(multipart(rota).file(foto("image/png", PNG)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(multipart(rota).file(foto("image/png", PNG))
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"))))
                    .andExpect(status().isForbidden());
        }
        assertThat(categoria.getFotoUrl()).isNull();
    }

    @Test
    void deveRetornarNaoEncontradoParaCategoriaOuFotoInexistente() throws Exception {
        mockMvc.perform(multipart(rotaDefinitiva(Long.MAX_VALUE)).file(foto("image/png", PNG)).with(admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart(rotaTemporaria(Long.MAX_VALUE)).file(foto("image/png", PNG)).with(admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(rotaLeitura(Long.MAX_VALUE))).andExpect(status().isNotFound());

        Categoria semFoto = categoriaRepository.saveAndFlush(Categoria.builder().nome(nomeUnico()).build());
        mockMvc.perform(get(rotaLeitura(semFoto.getId()))).andExpect(status().isNotFound());
        semFoto.setFotoUrl("https://cdn.wonder.dev/categoria-antiga.png");
        categoriaRepository.flush();
        mockMvc.perform(get(rotaLeitura(semFoto.getId()))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @MethodSource("arquivosInvalidos")
    void deveRejeitarArquivoInvalidoPreservandoFotoAnterior(String tipo, byte[] bytes, int codigo) throws Exception {
        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder()
                .nome(nomeUnico()).fotoUrl("https://cdn.wonder.dev/original.png").build());

        mockMvc.perform(multipart(rotaDefinitiva(categoria.getId())).file(foto(tipo, bytes)).with(admin()))
                .andExpect(status().is(codigo));

        categoriaRepository.flush();
        entityManager.clear();
        assertThat(categoriaRepository.findById(categoria.getId()).orElseThrow().getFotoUrl())
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
        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder().nome(nomeUnico()).build());
        mockMvc.perform(multipart(rotaDefinitiva(categoria.getId())).with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(rotaDefinitiva(categoria.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}").with(admin()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void deveAceitarWebpNoLimiteExatoDeCincoMbEExporFotoNoCatalogo() throws Exception {
        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder().nome(nomeUnico()).build());
        byte[] webp = new byte[5 * 1024 * 1024];
        byte[] assinatura = "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(assinatura, 0, webp, 0, assinatura.length);

        mockMvc.perform(multipart(rotaDefinitiva(categoria.getId())).file(foto("image/webp", webp)).with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get(rotaLeitura(categoria.getId())))
                .andExpect(status().isOk()).andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(webp));
        mockMvc.perform(get("/catalogo/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)].foto_url".formatted(categoria.getId()))
                        .value(org.hamcrest.Matchers.contains(url(categoria.getId()))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private MockMultipartFile foto(String tipo, byte[] bytes) {
        return new MockMultipartFile("file", "foto", tipo, bytes);
    }

    private String nomeUnico() {
        return "Categoria foto " + UUID.randomUUID();
    }

    private String rotaDefinitiva(Long id) {
        return "/admin/categorias/%s/foto".formatted(id);
    }

    private String rotaTemporaria(Long id) {
        return "/catalogo/admin/categorias/%s/foto".formatted(id);
    }

    private String rotaLeitura(Long id) {
        return "/catalogo/categorias/%s/foto".formatted(id);
    }

    private String url(Long id) {
        return "http://api.wonder.test" + rotaLeitura(id);
    }

    private static String endpointMinio() {
        return "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000));
    }
}
