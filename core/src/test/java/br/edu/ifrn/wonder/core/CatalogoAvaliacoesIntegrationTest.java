package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.Avaliacao;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.AvaliacaoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoAvaliacoesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AvaliacaoRepository avaliacaoRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveListarSemLoginSomenteAvaliacoesDoPrestadorOrdenadasPorIdComContratoPublico() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, true);
        Avaliacao primeira = criarAvaliacao(prestador, 1);
        Avaliacao segunda = criarAvaliacao(prestador, 5);
        criarAvaliacao(criarPrestador(StatusPrestador.ATIVO, true), 3);

        mockMvc.perform(get(rota(prestador.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]", aMapWithSize(4)))
                .andExpect(jsonPath("$[0].id").value(primeira.getId().intValue()))
                .andExpect(jsonPath("$[0].agendamento_id").value(primeira.getAgendamento().getId().intValue()))
                .andExpect(jsonPath("$[0].prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$[0].nota").value(1))
                .andExpect(jsonPath("$[0].cliente_id").doesNotExist())
                .andExpect(jsonPath("$[0].cliente").doesNotExist())
                .andExpect(jsonPath("$[0].agendamento").doesNotExist())
                .andExpect(jsonPath("$[1]", aMapWithSize(4)))
                .andExpect(jsonPath("$[1].id").value(segunda.getId().intValue()))
                .andExpect(jsonPath("$[1].agendamento_id").value(segunda.getAgendamento().getId().intValue()))
                .andExpect(jsonPath("$[1].prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$[1].nota").value(5));
    }

    @Test
    void deveRetornarListaVaziaParaPrestadorPublicoSemAvaliacoes() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, true);
        mockMvc.perform(get(rota(prestador.getId())))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @EnumSource(value = StatusPrestador.class, mode = EnumSource.Mode.EXCLUDE, names = "ATIVO")
    void deveOcultarAvaliacoesDePrestadorSemStatusPublicoMesmoComAtivoTrue(StatusPrestador situacao) throws Exception {
        Prestador prestador = criarPrestador(situacao, true);
        criarAvaliacao(prestador, 4);
        mockMvc.perform(get(rota(prestador.getId()))).andExpect(status().isNotFound());
    }

    @Test
    void deveOcultarAvaliacoesDePrestadorAtivoComPublicacaoDesabilitada() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, false);
        criarAvaliacao(prestador, 4);
        mockMvc.perform(get(rota(prestador.getId()))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_ADMIN"})
    void consultaPublicaNaoDeveExporCadastroOcultoMesmoParaDonoOuAdministrador(String papel) throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.PENDENTE, false);
        criarAvaliacao(prestador, 4);
        mockMvc.perform(get(rota(prestador.getId()))
                        .with(jwt().jwt(token -> token.subject(prestador.getPerfilUsuario().getKeycloakId().toString()))
                                .authorities(new SimpleGrantedAuthority(papel))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar404ParaPrestadorInexistente() throws Exception {
        mockMvc.perform(get(rota(Long.MAX_VALUE))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalido", "9999999999999999999999999"})
    void deveRetornar400ParaIdInvalido(String id) throws Exception {
        mockMvc.perform(get("/catalogo/prestadores/{id}/avaliacoes", id)).andExpect(status().isBadRequest());
    }

    @Test
    void deveManterAvaliacaoHistoricaQuandoServicoForInativado() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, true);
        Avaliacao avaliacao = criarAvaliacao(prestador, 4);
        avaliacao.getAgendamento().getServico().setStatus(StatusServico.INATIVO);
        servicoRepository.flush();
        mockMvc.perform(get(rota(prestador.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(avaliacao.getId().intValue()));
    }

    @Test
    void bancoDeveImpedirDuasAvaliacoesParaMesmoAgendamento() {
        Avaliacao original = criarAvaliacao(criarPrestador(StatusPrestador.ATIVO, true), 4);
        assertThatThrownBy(() -> avaliacaoRepository.saveAndFlush(Avaliacao.builder()
                .agendamento(original.getAgendamento()).nota(5).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_avaliacoes_agendamento");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void bancoDeveImpedirNotaForaDeUmACinco(int nota) {
        Agendamento agendamento = criarAgendamento(criarPrestador(StatusPrestador.ATIVO, true));
        assertThatThrownBy(() -> avaliacaoRepository.saveAndFlush(Avaliacao.builder()
                .agendamento(agendamento).nota(nota).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_avaliacoes_nota");
    }

    @Test
    void bancoDeveExigirAgendamentoExistente() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO avaliacoes (agendamento_id, nota) VALUES (?, ?)", Long.MAX_VALUE, 5))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_avaliacoes_agendamento");
    }

    @Test
    void bancoNaoDeveApagarAgendamentoQuePossuiAvaliacao() {
        Avaliacao avaliacao = criarAvaliacao(criarPrestador(StatusPrestador.ATIVO, true), 4);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM agendamentos WHERE id = ?",
                avaliacao.getAgendamento().getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_avaliacoes_agendamento");
    }

    private Prestador criarPrestador(StatusPrestador situacao, boolean ativo) {
        PerfilUsuario perfil = perfilUsuarioRepository.saveAndFlush(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.saveAndFlush(Prestador.builder().perfilUsuario(perfil)
                .nomeEstab("Studio avaliações").status(situacao).ativo(ativo).build());
    }

    private Agendamento criarAgendamento(Prestador prestador) {
        PerfilUsuario cliente = perfilUsuarioRepository.saveAndFlush(new PerfilUsuario(UUID.randomUUID()));
        Servico servico = servicoRepository.saveAndFlush(Servico.builder().prestador(prestador).nome("Corte")
                .preco(new BigDecimal("40.00")).duracaoMin(30).build());
        LocalDateTime inicio = LocalDateTime.of(2030, 1, 7, 9, 0);
        return agendamentoRepository.saveAndFlush(Agendamento.builder().cliente(cliente).prestador(prestador)
                .servico(servico).inicio(inicio).fim(inicio.plusMinutes(30)).status(StatusAgendamento.CONCLUIDO).build());
    }

    private Avaliacao criarAvaliacao(Prestador prestador, int nota) {
        return avaliacaoRepository.saveAndFlush(Avaliacao.builder()
                .agendamento(criarAgendamento(prestador)).nota(nota).build());
    }

    private String rota(Long prestadorId) {
        return "/catalogo/prestadores/%s/avaliacoes".formatted(prestadorId);
    }
}
