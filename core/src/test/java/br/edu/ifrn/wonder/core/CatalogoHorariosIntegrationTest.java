package br.edu.ifrn.wonder.core;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoHorariosIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrestadorRepository prestadorRepository;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Autowired
    private HorarioAtendimentoRepository horarioAtendimentoRepository;

    @Test
    void deveListarSemLoginSomenteHorariosDoPrestadorOrdenadosPorDiaEHora() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, true);
        HorarioAtendimento quarta = criarHorario(prestador, 3, "09:00", "18:00");
        HorarioAtendimento segundaTarde = criarHorario(prestador, 1, "14:00", "18:00");
        HorarioAtendimento segundaManha = criarHorario(prestador, 1, "09:00", "12:00");
        criarHorario(criarPrestador(StatusPrestador.ATIVO, true), 2, "08:00", "17:00");
        horarioAtendimentoRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/horarios", prestador.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)))
                .andExpect(jsonPath("$[0].id").value(segundaManha.getId().intValue()))
                .andExpect(jsonPath("$[0].prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$[0].dia_semana").value(1))
                .andExpect(jsonPath("$[0].hora_inicio").value("09:00:00"))
                .andExpect(jsonPath("$[0].hora_fim").value("12:00:00"))
                .andExpect(jsonPath("$[1].id").value(segundaTarde.getId().intValue()))
                .andExpect(jsonPath("$[1].prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$[1].dia_semana").value(1))
                .andExpect(jsonPath("$[1].hora_inicio").value("14:00:00"))
                .andExpect(jsonPath("$[2].id").value(quarta.getId().intValue()))
                .andExpect(jsonPath("$[2].prestador_id").value(prestador.getId().intValue()))
                .andExpect(jsonPath("$[2].dia_semana").value(3));
    }

    @Test
    void deveRetornarListaVaziaQuandoPrestadorPublicoNaoTemHorarios() throws Exception {
        Prestador prestador = criarPrestador(StatusPrestador.ATIVO, true);
        prestadorRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/horarios", prestador.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deveRetornar404QuandoPrestadorNaoExiste() throws Exception {
        mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/horarios", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar404ParaPrestadoresNaoPublicaveisMesmoComHorariosCadastrados() throws Exception {
        for (StatusPrestador status : StatusPrestador.values()) {
            for (boolean ativo : new boolean[] {false, true}) {
                if (status == StatusPrestador.ATIVO && ativo) {
                    continue;
                }
                Prestador prestador = criarPrestador(status, ativo);
                criarHorario(prestador, 1, "09:00", "18:00");
                horarioAtendimentoRepository.flush();

                mockMvc.perform(get("/catalogo/prestadores/{prestadorId}/horarios", prestador.getId()))
                        .andExpect(status().isNotFound());
            }
        }
    }

    private Prestador criarPrestador(StatusPrestador status, boolean ativo) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder()
                .perfilUsuario(perfil).nomeEstab("Studio horarios").status(status).ativo(ativo).build());
    }

    private HorarioAtendimento criarHorario(Prestador prestador, int diaSemana, String inicio, String fim) {
        return horarioAtendimentoRepository.save(HorarioAtendimento.builder()
                .prestador(prestador).diaSemana(diaSemana)
                .horaInicio(LocalTime.parse(inicio)).horaFim(LocalTime.parse(fim)).build());
    }
}
