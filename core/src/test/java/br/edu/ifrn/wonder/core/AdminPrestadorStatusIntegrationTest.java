package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import br.edu.ifrn.wonder.core.service.KeycloakAdminService;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPrestadorStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private HorarioAtendimentoRepository horarioAtendimentoRepository;
    @MockitoBean
    private KeycloakAdminService keycloakAdminService;

    @Test
    void deveListarPendentesEDetalharCadastroComServicosEHorarios() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        criarServicoEHorario(prestador);

        mockMvc.perform(get("/admin/prestadores/pendentes").with(admin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(prestador.getId().intValue())))
                .andExpect(jsonPath("$[?(@.id == %s)].usuario_id".formatted(prestador.getId()))
                        .value(hasItem(prestador.getPerfilUsuario().getKeycloakId().toString())))
                .andExpect(jsonPath("$[?(@.id == %s)].nome_estab".formatted(prestador.getId()))
                        .value(hasItem("Studio admin")));

        mockMvc.perform(get("/admin/prestadores/{id}", prestador.getId()).with(admin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pendente"))
                .andExpect(jsonPath("$.servicos.length()").value(1))
                .andExpect(jsonPath("$.servicos[0].prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$.servicos[0].duracao_min").value(30))
                .andExpect(jsonPath("$.horarios.length()").value(1))
                .andExpect(jsonPath("$.horarios[0].dia_semana").value(1));
    }

    @Test
    void deveAprovarCadastroPendente() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .with(admin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ativo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ativo"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.aprovado_por").value(adminId.toString()))
                .andExpect(jsonPath("$.motivo_rejeicao").doesNotExist());

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.ATIVO);
        assertThat(prestador.isAtivo()).isTrue();
        assertThat(prestador.getAprovadoEm()).isNotNull();
        assertThat(prestador.getMotivoRejeicao()).isNull();
        verify(keycloakAdminService).adicionarRoleProvider(prestador.getPerfilUsuario().getKeycloakId());
    }

    @Test
    void deveSolicitarCorrecaoComMotivo() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);

        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .with(admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"rascunho\",\"motivo_rejeicao\":\"  Corrigir documento  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rascunho"))
                .andExpect(jsonPath("$.motivo_rejeicao").value("Corrigir documento"));

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.RASCUNHO);
        assertThat(prestador.getMotivoRejeicao()).isEqualTo("Corrigir documento");
    }

    @Test
    void deveRejeitarSemAlterarQuandoMotivoNaoFoiInformado() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);

        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .with(admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"rejeitado\",\"motivo_rejeicao\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
        assertThat(prestador.getMotivoRejeicao()).isNull();
    }

    @Test
    void deveSuspenderEReativarPreservandoDadosDaAprovacao() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO);
        LocalDateTime aprovadoEm = prestador.getAprovadoEm();
        String aprovadoPor = prestador.getAprovadoPor();

        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .with(admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"suspenso\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("suspenso"))
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .with(admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ativo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ativo"))
                .andExpect(jsonPath("$.ativo").value(true));

        assertThat(prestador.getAprovadoEm()).isEqualTo(aprovadoEm);
        assertThat(prestador.getAprovadoPor()).isEqualTo(aprovadoPor);
        verify(keycloakAdminService).adicionarRoleProvider(prestador.getPerfilUsuario().getKeycloakId());
    }

    @Test
    void deveBloquearAutoaprovacaoETransicaoInvalida() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);

        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .with(admin(prestador.getPerfilUsuario().getKeycloakId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ativo\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .with(admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"suspenso\"}"))
                .andExpect(status().isConflict());

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
    }

    @Test
    void deveValidarAutorizacaoStatusEPrestador() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE);
        String corpo = "{\"status\":\"ATIVO\"}";

        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/admin/prestadores/{id}/status", prestador.getId())
                        .with(admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/prestadores/{id}", Long.MAX_VALUE).with(admin(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin(UUID adminId) {
        return jwt().jwt(token -> token.subject(adminId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private Prestador criarPrestador(StatusPrestador statusPrestador) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder()
                .perfilUsuario(perfil)
                .nomeEstab("Studio admin")
                .documento("12345678900")
                .cidade("Natal")
                .estado("RN")
                .status(statusPrestador)
                .ativo(statusPrestador == StatusPrestador.ATIVO)
                .enviadoEm(LocalDateTime.of(2026, 10, 1, 10, 0))
                .aprovadoEm(statusPrestador == StatusPrestador.ATIVO
                        ? LocalDateTime.of(2026, 10, 2, 10, 0) : null)
                .aprovadoPor(statusPrestador == StatusPrestador.ATIVO ? "admin-original" : null)
                .build());
    }

    private void criarServicoEHorario(Prestador prestador) {
        Categoria categoria = categoriaRepository.save(Categoria.builder()
                .nome("Categoria admin " + UUID.randomUUID())
                .status(StatusCategoria.ATIVA)
                .build());
        servicoRepository.save(Servico.builder()
                .prestador(prestador)
                .categoria(categoria)
                .nome("Servico admin")
                .preco(new BigDecimal("50.00"))
                .duracaoMin(30)
                .status(StatusServico.ATIVO)
                .build());
        horarioAtendimentoRepository.save(HorarioAtendimento.builder()
                .prestador(prestador)
                .diaSemana(1)
                .horaInicio(LocalTime.of(9, 0))
                .horaFim(LocalTime.of(12, 0))
                .build());
    }
}
