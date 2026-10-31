package br.edu.ifrn.wonder.core;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgendamentoCriacaoListagemIntegrationTest {

    private static final LocalDateTime SEGUNDA_09H = LocalDateTime.of(2099, 1, 5, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Autowired
    private PrestadorRepository prestadorRepository;

    @Autowired
    private ServicoRepository servicoRepository;

    @Autowired
    private HorarioAtendimentoRepository horarioAtendimentoRepository;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Test
    void deveCriarAgendamentoPendenteNoHorarioDisponivel() throws Exception {
        UUID clienteKeycloakId = UUID.randomUUID();
        PerfilUsuario cliente = criarPerfil(clienteKeycloakId);
        Prestador prestador = criarPrestadorAtivo("Studio agenda");
        Servico servico = criarServico(prestador, "Corte", 60, StatusServico.ATIVO);
        criarHorario(prestador, 1, "09:00", "12:00");

        mockMvc.perform(post("/agendamentos")
                        .with(usuario(clienteKeycloakId, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(prestador.getId(), servico.getId(), SEGUNDA_09H)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.cliente_id").value(cliente.getId().toString()))
                .andExpect(jsonPath("$.prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$.servico_id").value(servico.getId().intValue()))
                .andExpect(jsonPath("$.inicio").value("2099-01-05T09:00:00"))
                .andExpect(jsonPath("$.status").value("pendente"));

        Agendamento salvo = agendamentoRepository
                .findByClienteKeycloakIdOrderByInicioDesc(clienteKeycloakId)
                .getFirst();
        org.assertj.core.api.Assertions.assertThat(salvo.getFim()).isEqualTo(SEGUNDA_09H.plusHours(1));
    }

    @Test
    void deveRejeitarCriacaoSemAutenticacao() throws Exception {
        mockMvc.perform(post("/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(1L, 1L, SEGUNDA_09H)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRejeitarHorarioForaDaAgenda() throws Exception {
        UUID clienteKeycloakId = UUID.randomUUID();
        criarPerfil(clienteKeycloakId);
        Prestador prestador = criarPrestadorAtivo("Studio agenda");
        Servico servico = criarServico(prestador, "Corte", 60, StatusServico.ATIVO);
        criarHorario(prestador, 1, "09:00", "12:00");

        mockMvc.perform(post("/agendamentos")
                        .with(usuario(clienteKeycloakId, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(prestador.getId(), servico.getId(), SEGUNDA_09H.plusMinutes(30))))
                .andExpect(status().isConflict());
    }

    @Test
    void deveImpedirSobreposicaoDeAgendamentos() throws Exception {
        UUID primeiroCliente = UUID.randomUUID();
        UUID segundoCliente = UUID.randomUUID();
        criarPerfil(primeiroCliente);
        criarPerfil(segundoCliente);
        Prestador prestador = criarPrestadorAtivo("Studio agenda");
        Servico servico = criarServico(prestador, "Corte", 60, StatusServico.ATIVO);
        criarHorario(prestador, 1, "09:00", "12:00");

        mockMvc.perform(post("/agendamentos")
                        .with(usuario(primeiroCliente, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(prestador.getId(), servico.getId(), SEGUNDA_09H)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/agendamentos")
                        .with(usuario(segundoCliente, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(prestador.getId(), servico.getId(), SEGUNDA_09H)))
                .andExpect(status().isConflict());
    }

    @Test
    void deveRejeitarServicoInativoOuDeOutroPrestador() throws Exception {
        UUID clienteKeycloakId = UUID.randomUUID();
        criarPerfil(clienteKeycloakId);
        Prestador prestador = criarPrestadorAtivo("Studio agenda");
        Prestador outroPrestador = criarPrestadorAtivo("Outro studio");
        Servico servicoInativo = criarServico(prestador, "Corte", 60, StatusServico.INATIVO);
        Servico servicoDeOutroPrestador = criarServico(outroPrestador, "Maquiagem", 60, StatusServico.ATIVO);
        criarHorario(prestador, 1, "09:00", "12:00");

        mockMvc.perform(post("/agendamentos")
                        .with(usuario(clienteKeycloakId, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(prestador.getId(), servicoInativo.getId(), SEGUNDA_09H)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/agendamentos")
                        .with(usuario(clienteKeycloakId, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(prestador.getId(), servicoDeOutroPrestador.getId(), SEGUNDA_09H)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveListarSomenteAgendamentosDoCliente() throws Exception {
        PerfilUsuario cliente = criarPerfil(UUID.randomUUID());
        PerfilUsuario outroCliente = criarPerfil(UUID.randomUUID());
        Prestador prestador = criarPrestadorAtivo("Studio agenda");
        Servico servico = criarServico(prestador, "Corte", 60, StatusServico.ATIVO);
        criarAgendamento(cliente, prestador, servico, SEGUNDA_09H);
        criarAgendamento(outroCliente, prestador, servico, SEGUNDA_09H.plusHours(1));

        mockMvc.perform(get("/agendamentos")
                        .with(usuario(cliente.getKeycloakId(), "ROLE_CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cliente_id").value(cliente.getId().toString()))
                .andExpect(jsonPath("$[0].inicio").value("2099-01-05T09:00:00"));
    }

    @Test
    void deveListarAgendaDoPrestadorFiltradaPorData() throws Exception {
        PerfilUsuario cliente = criarPerfil(UUID.randomUUID());
        Prestador prestador = criarPrestadorAtivo("Studio agenda");
        Prestador outroPrestador = criarPrestadorAtivo("Outro studio");
        Servico servico = criarServico(prestador, "Corte", 60, StatusServico.ATIVO);
        Servico outroServico = criarServico(outroPrestador, "Maquiagem", 60, StatusServico.ATIVO);
        criarAgendamento(cliente, prestador, servico, SEGUNDA_09H);
        criarAgendamento(cliente, prestador, servico, SEGUNDA_09H.plusDays(1));
        criarAgendamento(cliente, outroPrestador, outroServico, SEGUNDA_09H);

        mockMvc.perform(get("/agendamentos")
                        .param("data", "2099-01-05")
                        .with(usuario(prestador.getPerfilUsuario().getKeycloakId(), "ROLE_PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$[0].inicio").value("2099-01-05T09:00:00"));
    }

    @Test
    void devePermitirQueAdminListeTodosOsAgendamentos() throws Exception {
        PerfilUsuario cliente = criarPerfil(UUID.randomUUID());
        Prestador primeiroPrestador = criarPrestadorAtivo("Studio um");
        Prestador segundoPrestador = criarPrestadorAtivo("Studio dois");
        Servico primeiroServico = criarServico(primeiroPrestador, "Corte", 60, StatusServico.ATIVO);
        Servico segundoServico = criarServico(segundoPrestador, "Maquiagem", 60, StatusServico.ATIVO);
        criarAgendamento(cliente, primeiroPrestador, primeiroServico, SEGUNDA_09H);
        criarAgendamento(cliente, segundoPrestador, segundoServico, SEGUNDA_09H.plusHours(1));

        mockMvc.perform(get("/agendamentos")
                        .param("data", "2099-01-05")
                        .with(usuario(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].inicio").value("2099-01-05T10:00:00"))
                .andExpect(jsonPath("$[1].inicio").value("2099-01-05T09:00:00"));
    }

    @Test
    void deveRejeitarListagemSemAutenticacao() throws Exception {
        mockMvc.perform(get("/agendamentos"))
                .andExpect(status().isUnauthorized());
    }

    private PerfilUsuario criarPerfil(UUID keycloakId) {
        return perfilUsuarioRepository.save(new PerfilUsuario(keycloakId));
    }

    private Prestador criarPrestadorAtivo(String nome) {
        PerfilUsuario perfil = criarPerfil(UUID.randomUUID());
        return prestadorRepository.save(Prestador.builder()
                .perfilUsuario(perfil)
                .nomeEstab(nome)
                .status(StatusPrestador.ATIVO)
                .ativo(true)
                .build());
    }

    private Servico criarServico(
            Prestador prestador,
            String nome,
            int duracaoMin,
            StatusServico status) {
        return servicoRepository.save(Servico.builder()
                .prestador(prestador)
                .nome(nome)
                .preco(new BigDecimal("50.00"))
                .duracaoMin(duracaoMin)
                .status(status)
                .build());
    }

    private void criarHorario(Prestador prestador, int diaSemana, String inicio, String fim) {
        horarioAtendimentoRepository.save(HorarioAtendimento.builder()
                .prestador(prestador)
                .diaSemana(diaSemana)
                .horaInicio(LocalTime.parse(inicio))
                .horaFim(LocalTime.parse(fim))
                .build());
    }

    private Agendamento criarAgendamento(
            PerfilUsuario cliente,
            Prestador prestador,
            Servico servico,
            LocalDateTime inicio) {
        return agendamentoRepository.save(Agendamento.builder()
                .cliente(cliente)
                .prestador(prestador)
                .servico(servico)
                .inicio(inicio)
                .fim(inicio.plusMinutes(servico.getDuracaoMin()))
                .status(StatusAgendamento.PENDENTE)
                .build());
    }

    private RequestPostProcessor usuario(UUID keycloakId, String papel) {
        return jwt()
                .jwt(token -> token.subject(keycloakId.toString()))
                .authorities(new SimpleGrantedAuthority(papel));
    }

    private String corpo(Long prestadorId, Long servicoId, LocalDateTime inicio) {
        return """
                {
                  "prestador_id": %d,
                  "servico_id": %d,
                  "inicio": "%s"
                }
                """.formatted(prestadorId, servicoId, inicio);
    }
}
