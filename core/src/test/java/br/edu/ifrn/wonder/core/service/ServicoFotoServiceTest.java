package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;

@ExtendWith(MockitoExtension.class)
class ServicoFotoServiceTest {

    @Mock
    private PrestadorRepository prestadorRepository;
    @Mock
    private ServicoRepository servicoRepository;
    @Mock
    private ArmazenamentoFotoService armazenamentoFotoService;

    private ServicoFotoService service;
    private Prestador prestador;
    private Servico servico;
    private UUID dono;

    @BeforeEach
    void preparar() {
        service = new ServicoFotoService(prestadorRepository, servicoRepository,
                armazenamentoFotoService, "http://api.wonder.test/");
        dono = UUID.randomUUID();
        prestador = Prestador.builder().id(1L).perfilUsuario(new PerfilUsuario(dono))
                .nomeEstab("Studio").status(StatusPrestador.ATIVO).ativo(true).build();
        servico = Servico.builder().id(2L).prestador(prestador).nome("Serviço")
                .preco(new BigDecimal("40.00")).duracaoMin(30).status(StatusServico.INATIVO)
                .fotoUrl("https://cdn.wonder.dev/original.png").build();
    }

    @Test
    void devePreservarFotoEDadosQuandoArmazenamentoFalhar() {
        MockMultipartFile arquivo = new MockMultipartFile("file", "foto.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));
        when(servicoRepository.findByIdAndPrestadorId(2L, 1L)).thenReturn(Optional.of(servico));
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Armazenamento indisponível"))
                .when(armazenamentoFotoService).salvar("prestadores/1/servicos/2/foto", arquivo);

        assertThatThrownBy(() -> service.atualizarFoto(1L, 2L, dono, arquivo))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(servico.getFotoUrl()).isEqualTo("https://cdn.wonder.dev/original.png");
        assertThat(servico.getStatus()).isEqualTo(StatusServico.INATIVO);
        assertThat(servico.getPreco()).isEqualByComparingTo("40.00");
        assertThat(servico.getDuracaoMin()).isEqualTo(30);
    }

    @Test
    void deveValidarDonoAntesDeBuscarServicoOuAcessarMinio() {
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));
        assertThatThrownBy(() -> service.atualizarFoto(1L, 2L, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(servicoRepository, armazenamentoFotoService);
    }

    @Test
    void deveValidarEstadoAntesDeBuscarServicoOuAcessarMinio() {
        prestador.setStatus(StatusPrestador.PENDENTE);
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));
        assertThatThrownBy(() -> service.atualizarFoto(1L, 2L, dono, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(servicoRepository, armazenamentoFotoService);
    }

    @Test
    void deveValidarVinculoDoServicoAntesDeAcessarMinio() {
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));
        when(servicoRepository.findByIdAndPrestadorId(2L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.atualizarFoto(1L, 2L, dono, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(armazenamentoFotoService);
    }
}
