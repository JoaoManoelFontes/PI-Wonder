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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import jakarta.persistence.EntityManager;

@Testcontainers
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoServicoFotoIntegrationTest {

    private static final String BUCKET = "wonder-fotos-servicos-teste";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6l8AAAAASUVORK5CYII=");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse("bitnamilegacy/minio:2025.7.23"))
            .withEnv("MINIO_ROOT_USER", "wonder")
            .withEnv("MINIO_ROOT_PASSWORD", "wonder-secret")
            // Evita que o health check encontre o servidor temporário do setup, que é reiniciado.
            .withEnv("MINIO_SKIP_CLIENT", "yes")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void configurarMinio(DynamicPropertyRegistry registry) {
        registry.add("wonder.minio.endpoint", CatalogoServicoFotoIntegrationTest::endpointMinio);
        registry.add("wonder.minio.access-key", () -> "wonder");
        registry.add("wonder.minio.secret-key", () -> "wonder-secret");
        registry.add("wonder.minio.bucket", () -> BUCKET);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void deveEnviarConsultarESubstituirSomenteFotoDoServico() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador, StatusServico.ATIVO);
        Servico outroServico = criarServico(prestador, StatusServico.ATIVO);
        outroServico.setFotoUrl("https://cdn.wonder.dev/outro-servico.png");
        Long prestadorId = prestador.getId();
        Long servicoId = servico.getId();
        UUID dono = prestador.getPerfilUsuario().getKeycloakId();

        mockMvc.perform(multipart(rota(prestadorId, servicoId)).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(sucesso())
                .andExpect(jsonPath("$", aMapWithSize(8)))
                .andExpect(jsonPath("$.id").value(servicoId.intValue()))
                .andExpect(jsonPath("$.prestador_id").value(prestadorId.intValue()))
                .andExpect(jsonPath("$.categoria_id").isEmpty())
                .andExpect(jsonPath("$.nome").value("Serviço foto"))
                .andExpect(jsonPath("$.preco").value(45.50))
                .andExpect(jsonPath("$.duracao_min").value(30))
                .andExpect(jsonPath("$.status").value("ativo"))
                .andExpect(jsonPath("$.foto_url").value(url(prestadorId, servicoId)));

        servicoRepository.flush();
        entityManager.clear();
        Servico salvo = servicoRepository.findById(servicoId).orElseThrow();
        assertThat(salvo.getFotoUrl()).isEqualTo(url(prestadorId, servicoId));
        assertThat(salvo.getNome()).isEqualTo("Serviço foto");
        assertThat(salvo.getPreco()).isEqualByComparingTo("45.50");
        assertThat(salvo.getDuracaoMin()).isEqualTo(30);
        assertThat(salvo.getCategoria()).isNull();
        assertThat(salvo.getStatus()).isEqualTo(StatusServico.ATIVO);
        assertThat(salvo.getPrestador().getStatus()).isEqualTo(StatusPrestador.ATIVO);
        assertThat(salvo.getPrestador().isAtivo()).isTrue();
        assertThat(salvo.getPrestador().getFotoUrl()).isEqualTo("foto-do-estabelecimento");
        assertThat(salvo.getPrestador().getPerfilUsuario().getFotoUrl()).isEqualTo("foto-do-perfil");
        assertThat(servicoRepository.findById(outroServico.getId()).orElseThrow().getFotoUrl())
                .isEqualTo("https://cdn.wonder.dev/outro-servico.png");

        mockMvc.perform(get(rota(prestadorId, servicoId)))
                .andExpect(sucesso()).andExpect(content().bytes(PNG))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().longValue("Content-Length", PNG.length))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/catalogo/prestadores/{id}/servicos", prestadorId))
                .andExpect(sucesso())
                .andExpect(jsonPath("$[?(@.id == %s)].foto_url".formatted(servicoId))
                        .value(org.hamcrest.Matchers.contains(url(prestadorId, servicoId))));

        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        mockMvc.perform(multipart(rota(prestadorId, servicoId)).file(foto("image/jpeg", jpeg))
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(sucesso()).andExpect(jsonPath("$.foto_url").value(url(prestadorId, servicoId)));
        mockMvc.perform(get(rota(prestadorId, servicoId)))
                .andExpect(sucesso()).andExpect(content().bytes(jpeg))
                .andExpect(content().contentType("image/jpeg"));

        MinioClient cliente = MinioClient.builder().endpoint(endpointMinio()).credentials("wonder", "wonder-secret").build();
        String objeto = "prestadores/%s/servicos/%s/foto".formatted(prestadorId, servicoId);
        assertThat(cliente.statObject(StatObjectArgs.builder().bucket(BUCKET).object(objeto).build()).size())
                .isEqualTo(jpeg.length);
        int quantidade = 0;
        for (var item : cliente.listObjects(ListObjectsArgs.builder().bucket(BUCKET)
                .prefix("prestadores/%s/servicos/%s/".formatted(prestadorId, servicoId)).recursive(true).build())) {
            assertThat(item.get().objectName()).isEqualTo(objeto);
            quantidade++;
        }
        assertThat(quantidade).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDENTE")
    void devePermitirUploadDoDonoSemMudarEstadoDoPrestador(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(situacao);
        Servico servico = criarServico(prestador, StatusServico.ATIVO);
        mockMvc.perform(multipart(rota(prestador.getId(), servico.getId())).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(sucesso());
        assertThat(prestador.getStatus()).isEqualTo(situacao);
        assertThat(prestador.isAtivo()).isEqualTo(situacao == StatusPrestador.ATIVO);
    }

    @ParameterizedTest
    @EnumSource(StatusServico.class)
    void devePermitirFotoSemReativarServicoEFiltrarConsultaPublica(StatusServico situacao) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador, situacao);
        UUID dono = prestador.getPerfilUsuario().getKeycloakId();
        String rota = rota(prestador.getId(), servico.getId());
        mockMvc.perform(multipart(rota).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(sucesso()).andExpect(jsonPath("$.status").value(situacao.name().toLowerCase(Locale.ROOT)));
        servicoRepository.flush();
        entityManager.clear();
        assertThat(servicoRepository.findById(servico.getId()).orElseThrow().getStatus()).isEqualTo(situacao);
        mockMvc.perform(get(rota)).andExpect(situacao == StatusServico.ATIVO ? sucesso() : status().isNotFound());
        mockMvc.perform(get(rota).with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(sucesso()).andExpect(content().bytes(PNG));
        mockMvc.perform(get(rota).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(sucesso());
    }

    @ParameterizedTest
    @EnumSource(StatusPrestador.class)
    void deveRestringirFotoDePrestadorNaoPublicadoAoDonoEAdministrador(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);
        Servico servico = criarServico(prestador, StatusServico.ATIVO);
        UUID dono = prestador.getPerfilUsuario().getKeycloakId();
        String rota = rota(prestador.getId(), servico.getId());
        mockMvc.perform(multipart(rota).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(dono.toString())))).andExpect(sucesso());
        prestador.setStatus(situacao);
        prestador.setAtivo(false);
        prestadorRepository.flush();
        mockMvc.perform(get(rota)).andExpect(status().isNotFound());
        mockMvc.perform(get(rota).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(rota).with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(sucesso());
        mockMvc.perform(get(rota).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(sucesso());
        prestador.setAtivo(true);
        prestadorRepository.flush();
        mockMvc.perform(get(rota)).andExpect(situacao == StatusPrestador.ATIVO ? sucesso() : status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_ADMIN"})
    void deveImpedirUploadDeOutroUsuarioInclusiveAdministrador(String papel) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador, StatusServico.ATIVO);
        mockMvc.perform(multipart(rota(prestador.getId(), servico.getId())).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority(papel))))
                .andExpect(status().isForbidden());
        assertThat(servico.getFotoUrl()).isNull();
    }

    @Test
    void deveValidarExistenciaEVinculoEntreServicoEPrestador() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servicoDeOutro = criarServico(criarPrestador(StatusPrestador.ATIVO), StatusServico.ATIVO);
        UUID dono = prestador.getPerfilUsuario().getKeycloakId();
        for (String rota : new String[]{rota(Long.MAX_VALUE, servicoDeOutro.getId()),
                rota(prestador.getId(), Long.MAX_VALUE), rota(prestador.getId(), servicoDeOutro.getId())}) {
            mockMvc.perform(multipart(rota).file(foto("image/png", PNG))
                            .with(jwt().jwt(token -> token.subject(dono.toString()))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(rota).with(jwt().jwt(token -> token.subject(dono.toString()))
                            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isNotFound());
        }
        assertThat(servicoDeOutro.getFotoUrl()).isNull();
    }

    @Test
    void deveBloquearUploadQuandoPrestadorEstaPendente() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        Servico servico = criarServico(prestador, StatusServico.ATIVO);
        mockMvc.perform(multipart(rota(prestador.getId(), servico.getId())).file(foto("image/png", PNG))
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isConflict());
        assertThat(servico.getFotoUrl()).isNull();
    }

    @Test
    void deveExigirAutenticacaoParaUpload() throws Exception {
        mockMvc.perform(multipart(rota(1L, 1L)).file(foto("image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveExigirParteFileECorpoMultipart() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador, StatusServico.ATIVO);
        UUID dono = prestador.getPerfilUsuario().getKeycloakId();
        String rota = rota(prestador.getId(), servico.getId());
        mockMvc.perform(multipart(rota).with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(jwt().jwt(token -> token.subject(dono.toString()))))
                .andExpect(status().isUnsupportedMediaType());
    }

    @ParameterizedTest
    @MethodSource("arquivosInvalidos")
    void deveValidarArquivoSemAlterarFotoAnterior(String tipo, byte[] bytes, int codigo) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador, StatusServico.ATIVO);
        servico.setFotoUrl("https://cdn.wonder.dev/original.png");
        mockMvc.perform(multipart(rota(prestador.getId(), servico.getId())).file(foto(tipo, bytes))
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().is(codigo));
        servicoRepository.flush();
        entityManager.clear();
        assertThat(servicoRepository.findById(servico.getId()).orElseThrow().getFotoUrl())
                .isEqualTo("https://cdn.wonder.dev/original.png");
    }

    static Stream<Arguments> arquivosInvalidos() {
        return Stream.of(Arguments.of("image/png", new byte[0], 400),
                Arguments.of(null, PNG, 400),
                Arguments.of("image/png", "texto".getBytes(StandardCharsets.UTF_8), 400),
                Arguments.of("image/png", new byte[5 * 1024 * 1024 + 1], 413));
    }

    @Test
    void deveRetornar404SemUploadPreservandoUrlsExternasNoDto() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador, StatusServico.ATIVO);
        String rota = rota(prestador.getId(), servico.getId());
        mockMvc.perform(get(rota)).andExpect(status().isNotFound());
        servico.setFotoUrl("https://cdn.wonder.dev/externa.png");
        mockMvc.perform(get(rota)).andExpect(status().isNotFound());
        mockMvc.perform(get("/catalogo/prestadores/{id}/servicos", prestador.getId()))
                .andExpect(sucesso()).andExpect(jsonPath("$[0].foto_url").value("https://cdn.wonder.dev/externa.png"));
    }

    @Test
    void deveAceitarWebpNoLimiteExatoDeCincoMb() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        Servico servico = criarServico(prestador, StatusServico.ATIVO);
        byte[] webp = new byte[5 * 1024 * 1024];
        byte[] assinatura = "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(assinatura, 0, webp, 0, assinatura.length);
        String rota = rota(prestador.getId(), servico.getId());
        mockMvc.perform(multipart(rota).file(foto("image/webp", webp))
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(sucesso());
        mockMvc.perform(get(rota)).andExpect(sucesso()).andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(webp));
    }

    private ResultMatcher sucesso() {
        return resultado -> {
            if (resultado.getResolvedException() != null) {
                throw new AssertionError("Falha no fluxo de foto do serviço: " + resultado.getRequest().getRequestURI(),
                        resultado.getResolvedException());
            }
            status().isOk().match(resultado);
        };
    }

    private Prestador criarPrestador(StatusPrestador situacao) {
        PerfilUsuario perfil = new PerfilUsuario(UUID.randomUUID());
        perfil.setFotoUrl("foto-do-perfil");
        perfilUsuarioRepository.saveAndFlush(perfil);
        return prestadorRepository.saveAndFlush(Prestador.builder().perfilUsuario(perfil).nomeEstab("Studio foto")
                .status(situacao).ativo(situacao == StatusPrestador.ATIVO).fotoUrl("foto-do-estabelecimento").build());
    }

    private Servico criarServico(Prestador prestador, StatusServico situacao) {
        return servicoRepository.saveAndFlush(Servico.builder().prestador(prestador).nome("Serviço foto")
                .preco(new BigDecimal("45.50")).duracaoMin(30).status(situacao).build());
    }

    private MockMultipartFile foto(String tipo, byte[] bytes) {
        return new MockMultipartFile("file", "foto", tipo, bytes);
    }

    private String rota(Long prestadorId, Long servicoId) {
        return "/catalogo/prestadores/%s/servicos/%s/foto".formatted(prestadorId, servicoId);
    }

    private String url(Long prestadorId, Long servicoId) {
        return "http://api.wonder.test" + rota(prestadorId, servicoId);
    }

    private static String endpointMinio() {
        return "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000));
    }
}
