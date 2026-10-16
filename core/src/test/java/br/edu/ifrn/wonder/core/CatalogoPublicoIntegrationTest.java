package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.dto.DisponibilidadeResponse;
import br.edu.ifrn.wonder.core.dto.HorarioAtendimentoPublicResponse;
import br.edu.ifrn.wonder.core.dto.HorarioDisponivelResponse;
import br.edu.ifrn.wonder.core.dto.ServicoPublicResponse;
import br.edu.ifrn.wonder.core.dto.PrestadorPublicResponse;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import br.edu.ifrn.wonder.core.service.CatalogoPublicoService;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CatalogoPublicoIntegrationTest {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private PrestadorRepository prestadorRepository;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Autowired
    private CatalogoPublicoService catalogoPublicoService;

    @Autowired
    private ServicoRepository servicoRepository;

    @Autowired
    private HorarioAtendimentoRepository horarioAtendimentoRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void deveListarSomenteCategoriasAtivasEmOrdemDeNome() {
        Categoria inativa = categoriaRepository.saveAndFlush(Categoria.builder()
                .nome("Categoria oculta").status(StatusCategoria.INATIVA).build());

        var categorias = catalogoPublicoService.listarCategoriasAtivas();

        assertThat(categorias).isNotEmpty().allMatch(categoria -> categoria.getStatus() == StatusCategoria.ATIVA);
        assertThat(categorias).extracting(Categoria::getNome).isSorted();
        assertThat(categorias).extracting(Categoria::getId).doesNotContain(inativa.getId());
        assertThatThrownBy(() -> catalogoPublicoService.buscarCategoriaAtiva(inativa.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        Categoria ativa = categorias.getFirst();
        assertThat(catalogoPublicoService.buscarCategoriaAtiva(ativa.getId()).getId()).isEqualTo(ativa.getId());
    }

    @Test
    void deveExigirAtivoEAprovadoNaListagemENaBuscaPorId() {
        Prestador publico = criarPrestador("A Studio publico", StatusPrestador.ATIVO, true);
        criarPrestador("Z Studio publico", StatusPrestador.ATIVO, true);

        for (StatusPrestador status : StatusPrestador.values()) {
            for (boolean ativo : new boolean[] {false, true}) {
                if (status == StatusPrestador.ATIVO && ativo) {
                    continue;
                }
                Prestador oculto = criarPrestador("Studio oculto " + status + ativo, status, ativo);
                assertThatThrownBy(() -> catalogoPublicoService.buscarPrestadorPublico(oculto.getId()))
                        .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                                assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
            }
        }

        var prestadores = catalogoPublicoService.listarPrestadoresPublicos();

        assertThat(prestadores).hasSize(3).allMatch(prestador ->
                prestador.isAtivo() && prestador.getStatus() == StatusPrestador.ATIVO);
        assertThat(prestadores).extracting(Prestador::getNomeEstab).isSorted();
        assertThat(catalogoPublicoService.buscarPrestadorPublico(publico.getId()).getId()).isEqualTo(publico.getId());
        assertThat(PrestadorPublicResponse.from(publico).perfilUsuarioId()).isNotNull();
    }

    @Test
    void deveBuscarServicoEHorarioSomenteDoPrestadorPublicoInformado() {
        Prestador prestador = prestadorSeed();
        Servico servico = servicoRepository
                .findByPrestadorIdAndStatusOrderByNomeAsc(prestador.getId(), StatusServico.ATIVO).getFirst();
        var horario = horarioAtendimentoRepository
                .findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestador.getId()).getFirst();
        Prestador outro = criarPrestador("Outro Studio", StatusPrestador.ATIVO, true);

        assertThat(catalogoPublicoService.buscarServicoAtivo(prestador.getId(), servico.getId()).getId())
                .isEqualTo(servico.getId());
        assertThat(catalogoPublicoService.buscarHorarioAtendimento(prestador.getId(), horario.getId()).getId())
                .isEqualTo(horario.getId());

        assertThatThrownBy(() -> catalogoPublicoService.buscarServicoAtivo(outro.getId(), servico.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> catalogoPublicoService.buscarHorarioAtendimento(outro.getId(), horario.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> catalogoPublicoService.buscarServicoAtivo(prestador.getId(), Long.MAX_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> catalogoPublicoService.buscarHorarioAtendimento(prestador.getId(), Long.MAX_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        servico.setStatus(StatusServico.INATIVO);
        servicoRepository.flush();
        assertThatThrownBy(() -> catalogoPublicoService.buscarServicoAtivo(prestador.getId(), servico.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        prestador.setAtivo(false);
        prestadorRepository.flush();
        assertThatThrownBy(() -> catalogoPublicoService.buscarHorarioAtendimento(prestador.getId(), horario.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deveSerializarServicoEHorarioSemExporEntidadesRelacionadas() {
        Prestador prestador = prestadorSeed();
        Servico servico = servicoRepository
                .findByPrestadorIdAndStatusOrderByNomeAsc(prestador.getId(), StatusServico.ATIVO).getFirst();
        var horario = horarioAtendimentoRepository
                .findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestador.getId()).getFirst();

        String servicoJson = jsonMapper.writeValueAsString(ServicoPublicResponse.from(servico));
        String horarioJson = jsonMapper.writeValueAsString(HorarioAtendimentoPublicResponse.from(horario));

        assertThat(servicoJson).contains("\"prestador_id\":" + prestador.getId(),
                "\"categoria_id\":" + servico.getCategoria().getId(), "\"duracao_min\":", "\"foto_url\":",
                "\"status\":\"ativo\"").doesNotContain("\"prestador\":", "\"categoria\":", "perfilUsuario");
        assertThat(horarioJson).contains("\"prestador_id\":" + prestador.getId(), "\"dia_semana\":1",
                "\"hora_inicio\":\"09:00:00\"", "\"hora_fim\":\"18:00:00\"")
                .doesNotContain("\"prestador\":", "perfilUsuario");

        Servico semCategoria = servicoRepository.saveAndFlush(Servico.builder()
                .prestador(prestador).nome("Servico sem categoria").preco(new BigDecimal("25.50"))
                .duracaoMin(30).build());
        assertThat(ServicoPublicResponse.from(semCategoria).categoriaId()).isNull();
        assertThat(jsonMapper.writeValueAsString(ServicoPublicResponse.from(semCategoria)))
                .contains("\"categoria_id\":null", "\"preco\":25.50");
    }

    @Test
    void deveSerializarDisponibilidadeComIntervalosEListaVazia() {
        LocalDate data = LocalDate.of(2030, 1, 7);
        var intervalo = new HorarioDisponivelResponse(
                LocalDateTime.of(data, java.time.LocalTime.of(9, 0)),
                LocalDateTime.of(data, java.time.LocalTime.of(9, 45)));
        var disponibilidade = new DisponibilidadeResponse(1L, 2L, data, 45, List.of(intervalo));

        assertThat(jsonMapper.writeValueAsString(disponibilidade)).contains(
                "\"prestador_id\":1", "\"servico_id\":2", "\"data\":\"2030-01-07\"", "\"duracao_min\":45",
                "\"horarios\":[{", "\"inicio\":\"2030-01-07T09:00:00\"", "\"fim\":\"2030-01-07T09:45:00\"");
        assertThat(jsonMapper.writeValueAsString(new DisponibilidadeResponse(1L, 2L, data, 45, List.of())))
                .contains("\"horarios\":[]");
    }

    private Prestador prestadorSeed() {
        return prestadorRepository.findByPerfilUsuarioId(UUID.fromString("10000000-0000-0000-0000-000000000001"))
                .orElseThrow();
    }

    private Prestador criarPrestador(String nome, StatusPrestador status, boolean ativo) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.saveAndFlush(Prestador.builder()
                .perfilUsuario(perfil).nomeEstab(nome).status(status).ativo(ativo).build());
    }
}
