package br.edu.ifrn.wonder.worker.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import br.edu.ifrn.wonder.worker.Contratos;
import br.edu.ifrn.wonder.worker.messaging.ExportacaoPublisher;

class ExportacaoServiceTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void removeTemporarioAposSucessoOuFalhaDeUpload(boolean falhar) throws Exception {
        var contrato = new ContratoExportacaoService();
        var armazenamento = mock(ArmazenamentoExportacaoService.class);
        var publicador = mock(ExportacaoPublisher.class);
        var servico = new ExportacaoService(contrato, new GeradorCsvService(), armazenamento, publicador);
        var caminho = new AtomicReference<Path>();
        when(armazenamento.armazenar(any(), anyString(), any())).thenAnswer(chamada -> {
            Path arquivo = chamada.getArgument(2);
            caminho.set(arquivo);
            assertThat(arquivo).exists();
            assertThat(java.nio.file.Files.readString(arquivo)).startsWith("\uFEFF\"ID do usuário\"");
            if (falhar) throw new java.io.IOException("Falha simulada");
            return new br.edu.ifrn.wonder.worker.dto.ExportacaoConcluida(null, null, null, null, null, null, null);
        });
        var pedido = contrato.ler(Contratos.corpo("usuarios"));
        if (falhar) {
            assertThatThrownBy(() -> servico.processar(pedido)).isInstanceOf(java.io.IOException.class);
            verifyNoInteractions(publicador);
        } else {
            servico.processar(pedido);
            verify(publicador).publicar(any());
        }
        assertThat(caminho.get()).doesNotExist();
    }

    @org.junit.jupiter.api.Test
    void falhaDeAssinaturaNaoPublicaEFechaStream() throws Exception {
        var interno = mock(io.minio.MinioClient.class);
        var publico = mock(io.minio.MinioClient.class);
        var configuracao = new br.edu.ifrn.wonder.worker.config.MinioProperties(
                "http://interno:9000", "http://publico:9000", "", "", "wonder-exportacoes", 24);
        var armazenamento = new ArmazenamentoExportacaoService(interno, publico, configuracao);
        when(interno.bucketExists(any())).thenReturn(true);
        when(publico.getPresignedObjectUrl(any())).thenThrow(new IllegalStateException("Assinatura indisponível"));
        var publicador = mock(ExportacaoPublisher.class);
        var contrato = new ContratoExportacaoService();
        var servico = new ExportacaoService(contrato, new GeradorCsvService(), armazenamento, publicador);
        var entrada = new AtomicReference<java.io.InputStream>();
        when(interno.putObject(any())).thenAnswer(chamada -> {
            io.minio.PutObjectArgs argumentos = chamada.getArgument(0);
            entrada.set(argumentos.stream());
            assertThat(entrada.get().readAllBytes()).isNotEmpty();
            return null;
        });
        assertThatThrownBy(() -> servico.processar(contrato.ler(Contratos.corpo("usuarios"))))
                .isInstanceOf(IllegalStateException.class);
        verify(interno).putObject(any());
        verifyNoInteractions(publicador);
        assertThatThrownBy(() -> entrada.get().read()).isInstanceOf(java.io.IOException.class);
    }
}
