package br.edu.ifrn.wonder.core;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgendamentoDisponibilidadeIntegrationTest {

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

    @Test
    void deveListarHorariosDisponiveisSemAutenticacao() throws Exception {
        Prestador prestador = criarPrestador();
        Servico servico = criarServico(prestador);
        criarHorario(prestador, 1, "09:00", "12:00");

        mockMvc.perform(get("/agendamentos/disponibilidade")
                        .param("prestador_id", prestador.getId().toString())
                        .param("servico_id", servico.getId().toString())
                        .param("data", "2099-01-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$.servico_id").value(servico.getId().intValue()))
                .andExpect(jsonPath("$.data").value("2099-01-05"))
                .andExpect(jsonPath("$.duracao_min").value(60))
                .andExpect(jsonPath("$.horarios.length()").value(3))
                .andExpect(jsonPath("$.horarios[0].inicio").value("2099-01-05T09:00:00"))
                .andExpect(jsonPath("$.horarios[0].fim").value("2099-01-05T10:00:00"))
                .andExpect(jsonPath("$.horarios[2].inicio").value("2099-01-05T11:00:00"))
                .andExpect(jsonPath("$.horarios[2].fim").value("2099-01-05T12:00:00"));
    }

    @Test
    void devePermitirConsultaComAutenticacao() throws Exception {
        Prestador prestador = criarPrestador();
        Servico servico = criarServico(prestador);

        mockMvc.perform(get("/agendamentos/disponibilidade")
                        .param("prestador_id", prestador.getId().toString())
                        .param("servico_id", servico.getId().toString())
                        .param("data", "2099-01-05")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios").isEmpty());
    }

    @Test
    void deveRejeitarDataForaDoFormatoEsperado() throws Exception {
        mockMvc.perform(get("/agendamentos/disponibilidade")
                        .param("prestador_id", "1")
                        .param("servico_id", "1")
                        .param("data", "05-01-2099"))
                .andExpect(status().isBadRequest());
    }

    private Prestador criarPrestador() {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder()
                .perfilUsuario(perfil)
                .nomeEstab("Studio horarios")
                .status(StatusPrestador.ATIVO)
                .ativo(true)
                .build());
    }

    private Servico criarServico(Prestador prestador) {
        return servicoRepository.save(Servico.builder()
                .prestador(prestador)
                .nome("Servico de teste")
                .preco(new BigDecimal("50.00"))
                .duracaoMin(60)
                .status(StatusServico.ATIVO)
                .build());
    }

    private HorarioAtendimento criarHorario(
            Prestador prestador,
            int diaSemana,
            String inicio,
            String fim) {
        return horarioAtendimentoRepository.save(HorarioAtendimento.builder()
                .prestador(prestador)
                .diaSemana(diaSemana)
                .horaInicio(LocalTime.parse(inicio))
                .horaFim(LocalTime.parse(fim))
                .build());
    }
}
