package br.edu.ifrn.wonder.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@ExtendWith(MockitoExtension.class)
class PrestadorFotoServiceTest {

    @Mock
    private PrestadorRepository prestadorRepository;
    @Mock
    private ArmazenamentoFotoService armazenamentoFotoService;

    private PrestadorFotoService service;
    private Prestador prestador;
    private UUID dono;

    @BeforeEach
    void preparar() {
        service = new PrestadorFotoService(prestadorRepository, armazenamentoFotoService, "http://api.wonder.test/");
        dono = UUID.randomUUID();
        prestador = Prestador.builder().id(1L).perfilUsuario(new PerfilUsuario(dono))
                .nomeEstab("Studio").status(StatusPrestador.ATIVO).ativo(true)
                .fotoUrl("https://cdn.wonder.dev/original.png").build();
    }

    @Test
    void devePreservarFotoAnteriorQuandoArmazenamentoFalhar() {
        MockMultipartFile arquivo = new MockMultipartFile("file", "foto.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Armazenamento indisponível"))
                .when(armazenamentoFotoService).salvar("prestadores/1/foto", arquivo);

        assertThatThrownBy(() -> service.atualizarFoto(1L, dono, arquivo))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(prestador.getFotoUrl()).isEqualTo("https://cdn.wonder.dev/original.png");
        assertThat(prestador.getStatus()).isEqualTo(StatusPrestador.ATIVO);
        assertThat(prestador.isAtivo()).isTrue();
    }

    @Test
    void deveValidarPropriedadeAntesDeLerOuArmazenarArquivo() {
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));
        assertThatThrownBy(() -> service.atualizarFoto(1L, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(armazenamentoFotoService);
    }

    @Test
    void deveValidarStatusAntesDeLerOuArmazenarArquivo() {
        prestador.setStatus(StatusPrestador.PENDENTE);
        when(prestadorRepository.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(prestador));
        assertThatThrownBy(() -> service.atualizarFoto(1L, dono, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(armazenamentoFotoService);
    }

    @Test
    void deveImpedirLeituraDoArmazenamentoQuandoFotoNaoEstaPublicada() {
        prestador.setAtivo(false);
        when(prestadorRepository.findById(1L)).thenReturn(Optional.of(prestador));
        assertThatThrownBy(() -> service.buscarFoto(1L, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(armazenamentoFotoService);
    }
}
