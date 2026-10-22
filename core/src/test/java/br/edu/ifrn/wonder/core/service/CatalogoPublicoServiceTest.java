package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.AvaliacaoRepository;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;

@ExtendWith(MockitoExtension.class)
class CatalogoPublicoServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private PrestadorRepository prestadorRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private HorarioAtendimentoRepository horarioAtendimentoRepository;

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @InjectMocks
    private CatalogoPublicoService catalogoPublicoService;

    @Test
    void deveValidarPublicacaoDoPrestadorAntesDeConsultarAvaliacoes() {
        when(prestadorRepository.findByIdAndAtivoTrueAndStatus(1L, StatusPrestador.ATIVO))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> catalogoPublicoService.listarAvaliacoesDoPrestador(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(avaliacaoRepository);
    }

    @Test
    void deveRetornar404QuandoCategoriaNaoEstaDisponivel() {
        when(categoriaRepository.findByIdAndStatus(1L, StatusCategoria.ATIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogoPublicoService.buscarCategoriaAtiva(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao -> {
                    assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(excecao.getReason()).isEqualTo("Categoria não encontrada");
                });
    }

    @Test
    void deveRetornar404QuandoPrestadorNaoEstaDisponivel() {
        when(prestadorRepository.findByIdAndAtivoTrueAndStatus(1L, StatusPrestador.ATIVO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogoPublicoService.buscarPrestadorPublico(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao -> {
                    assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(excecao.getReason()).isEqualTo("Prestador não encontrado");
                });
    }

    @Test
    void deveRetornar404QuandoServicoNaoEstaDisponivelParaPrestador() {
        when(prestadorRepository.findByIdAndAtivoTrueAndStatus(1L, StatusPrestador.ATIVO))
                .thenReturn(Optional.of(Prestador.builder().id(1L).build()));
        when(servicoRepository.findByIdAndPrestadorIdAndStatus(2L, 1L, StatusServico.ATIVO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogoPublicoService.buscarServicoAtivo(1L, 2L))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao -> {
                    assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(excecao.getReason()).isEqualTo("Serviço não encontrado");
                });
    }

    @Test
    void deveRetornar404QuandoHorarioNaoPertenceAoPrestadorOuNaoExiste() {
        when(prestadorRepository.findByIdAndAtivoTrueAndStatus(1L, StatusPrestador.ATIVO))
                .thenReturn(Optional.of(Prestador.builder().id(1L).build()));
        when(horarioAtendimentoRepository.findByIdAndPrestadorId(2L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogoPublicoService.buscarHorarioAtendimento(1L, 2L))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao -> {
                    assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(excecao.getReason()).isEqualTo("Horário de atendimento não encontrado");
                });
    }

    @Test
    void deveValidarPrestadorPublicoAntesDeConsultarServicoOuHorario() {
        when(prestadorRepository.findByIdAndAtivoTrueAndStatus(1L, StatusPrestador.ATIVO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogoPublicoService.buscarServicoAtivo(1L, 2L))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> catalogoPublicoService.buscarHorarioAtendimento(1L, 2L))
                .isInstanceOfSatisfying(ResponseStatusException.class, excecao ->
                        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(servicoRepository, horarioAtendimentoRepository);
    }
}
