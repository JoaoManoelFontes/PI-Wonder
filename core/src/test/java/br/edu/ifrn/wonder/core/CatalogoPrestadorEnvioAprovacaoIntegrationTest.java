package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoPrestadorEnvioAprovacaoIntegrationTest {

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
    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, names = {"RASCUNHO", "REJEITADO"})
    void deveEnviarCadastroCompletoDoDonoParaAnaliseSemPublicar(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarCadastroCompleto(situacao);
        Long prestadorId = prestador.getId();
        UUID perfilId = prestador.getPerfilUsuario().getId();
        UUID keycloakId = prestador.getPerfilUsuario().getKeycloakId();
        LocalDateTime antesDoEnvio = LocalDateTime.now().minusSeconds(1);

        mockMvc.perform(post("/catalogo/prestadores/{id}/enviar-aprovacao", prestadorId)
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(prestadorId.intValue()))
                .andExpect(jsonPath("$.perfilUsuarioId").value(perfilId.toString()))
                .andExpect(jsonPath("$.nomeEstab").value("Studio envio"))
                .andExpect(jsonPath("$.status").value("pendente"))
                .andExpect(jsonPath("$.ativo").value(false));

        prestadorRepository.flush();
        entityManager.clear();
        Prestador salvo = prestadorRepository.findById(prestadorId).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusPrestador.PENDENTE);
        assertThat(salvo.isAtivo()).isFalse();
        assertThat(salvo.getEnviadoEm()).isAfter(antesDoEnvio).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(salvo.getMotivoRejeicao()).isNull();
        assertThat(salvo.getAprovadoEm()).isNull();
        assertThat(salvo.getAprovadoPor()).isEqualTo("revisor-anterior");
        assertThat(salvo.getPerfilUsuario().getId()).isEqualTo(perfilId);

        mockMvc.perform(get("/catalogo/prestadores/{id}", prestadorId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/prestadores/solicitacoes")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(prestadorId.intValue())));

        mockMvc.perform(post("/catalogo/prestadores/{id}/enviar-aprovacao", prestadorId)
                        .with(jwt().jwt(token -> token.subject(keycloakId.toString()))))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, names = {"PENDENTE", "ATIVO", "SUSPENSO"})
    void deveRejeitarEnvioEmStatusNaoPermitido(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarCadastroCompleto(situacao);
        LocalDateTime enviadoEm = prestador.getEnviadoEm();

        mockMvc.perform(post("/catalogo/prestadores/{id}/enviar-aprovacao", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isConflict());

        assertThat(prestador.getStatus()).isEqualTo(situacao);
        assertThat(prestador.getEnviadoEm()).isEqualTo(enviadoEm);
        assertThat(prestador.getMotivoRejeicao()).isEqualTo("Corrigir cadastro");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "documento", "servico", "categoria", "horario", "vinculoCategoria"})
    void deveValidarRequisitosAntesDeAlterarStatus(String requisito) throws Exception {
        Prestador prestador = criarCadastroCompleto(StatusPrestador.REJEITADO);
        Servico servico = servicoRepository
                .findByPrestadorIdAndStatusOrderByNomeAsc(prestador.getId(), StatusServico.ATIVO).getFirst();
        switch (requisito) {
            case "nome" -> prestador.setNomeEstab("   ");
            case "documento" -> prestador.setDocumento(null);
            case "servico" -> servico.setStatus(StatusServico.INATIVO);
            case "categoria" -> servico.getCategoria().setStatus(StatusCategoria.INATIVA);
            case "vinculoCategoria" -> servico.setCategoria(null);
            case "horario" -> horarioAtendimentoRepository.deleteAll(
                    horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestador.getId()));
            default -> throw new IllegalArgumentException("Requisito desconhecido");
        }
        LocalDateTime enviadoEm = prestador.getEnviadoEm();

        mockMvc.perform(post("/catalogo/prestadores/{id}/enviar-aprovacao", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isBadRequest());

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.REJEITADO);
        assertThat(prestador.getEnviadoEm()).isEqualTo(enviadoEm);
        assertThat(prestador.getMotivoRejeicao()).isEqualTo("Corrigir cadastro");
    }

    @Test
    void naoDeveUsarServicosCategoriasOuHorariosDeOutroPrestador() throws Exception {
        criarCadastroCompleto(StatusPrestador.RASCUNHO);
        Prestador prestador = criarPrestador(StatusPrestador.RASCUNHO);

        mockMvc.perform(post("/catalogo/prestadores/{id}/enviar-aprovacao", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))))
                .andExpect(status().isBadRequest());

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.RASCUNHO);
    }

    @Test
    void deveExigirAutenticacao() throws Exception {
        mockMvc.perform(post("/catalogo/prestadores/1/enviar-aprovacao"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveProibirEnvioPorOutroUsuarioMesmoComPapelAdmin() throws Exception {
        Prestador prestador = criarCadastroCompleto(StatusPrestador.REJEITADO);

        mockMvc.perform(post("/catalogo/prestadores/{id}/enviar-aprovacao", prestador.getId())
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.REJEITADO);
        assertThat(prestador.getMotivoRejeicao()).isEqualTo("Corrigir cadastro");
    }

    @Test
    void deveRetornar404QuandoCadastroNaoExiste() throws Exception {
        mockMvc.perform(post("/catalogo/prestadores/{id}/enviar-aprovacao", Long.MAX_VALUE)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400ParaIdInvalido() throws Exception {
        mockMvc.perform(post("/catalogo/prestadores/invalido/enviar-aprovacao")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());
    }

    private Prestador criarCadastroCompleto(StatusPrestador situacao) {
        Prestador prestador = criarPrestador(situacao);
        Categoria categoria = categoriaRepository.save(Categoria.builder()
                .nome("Categoria envio " + UUID.randomUUID()).status(StatusCategoria.ATIVA).build());
        servicoRepository.save(Servico.builder().prestador(prestador).categoria(categoria)
                .nome("Servico envio").duracaoMin(30).preco(new BigDecimal("30.00"))
                .status(StatusServico.ATIVO).build());
        horarioAtendimentoRepository.save(HorarioAtendimento.builder().prestador(prestador)
                .diaSemana(1).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(12, 0)).build());
        return prestador;
    }

    private Prestador criarPrestador(StatusPrestador situacao) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder().perfilUsuario(perfil)
                .nomeEstab("Studio envio").documento("12345678900").status(situacao)
                .ativo(situacao == StatusPrestador.ATIVO).motivoRejeicao("Corrigir cadastro")
                .aprovadoPor("revisor-anterior").enviadoEm(LocalDateTime.of(2026, 1, 1, 10, 0)).build());
    }
}
