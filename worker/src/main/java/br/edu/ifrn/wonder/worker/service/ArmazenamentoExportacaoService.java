package br.edu.ifrn.wonder.worker.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import br.edu.ifrn.wonder.worker.config.MinioProperties;
import br.edu.ifrn.wonder.worker.dto.ExportacaoConcluida;
import br.edu.ifrn.wonder.worker.dto.ExportacaoSolicitada;
import io.minio.*;
import io.minio.Http.Method;

@Service
public class ArmazenamentoExportacaoService {
    public static final String CONTENT_TYPE = "text/csv; charset=UTF-8";
    private final MinioClient minioInterno;
    private final MinioClient minioPublico;
    private final MinioProperties configuracao;

    public ArmazenamentoExportacaoService(@Qualifier("minioInterno") MinioClient minioInterno,
            @Qualifier("minioPublico") MinioClient minioPublico, MinioProperties configuracao) {
        this.minioInterno = minioInterno;
        this.minioPublico = minioPublico;
        this.configuracao = configuracao;
    }

    public ExportacaoConcluida armazenar(ExportacaoSolicitada pedido, String nomeArquivo, Path arquivo) throws Exception {
        garantirBucket();
        String chave = "exportacoes/" + pedido.tipo().name().toLowerCase(Locale.ROOT) + "/" + pedido.exportacaoId() + ".csv";
        String disposicao = "attachment; filename=\"" + nomeArquivo + "\"";
        try (var entrada = Files.newInputStream(arquivo)) {
            minioInterno.putObject(PutObjectArgs.builder().bucket(configuracao.exportBucket()).object(chave)
                    .stream(entrada, Files.size(arquivo), -1L).contentType(CONTENT_TYPE)
                    .headers(Map.of("Content-Disposition", disposicao)).build());
        }
        String url = minioPublico.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                .bucket(configuracao.exportBucket()).object(chave).expiry(configuracao.linkTtlHoras(), TimeUnit.HOURS)
                .extraQueryParams(Map.of("response-content-disposition", disposicao, "response-content-type", CONTENT_TYPE))
                .build());
        String dataAssinatura = java.util.Arrays.stream(java.net.URI.create(url).getRawQuery().split("&"))
                .filter(parametro -> parametro.startsWith("X-Amz-Date="))
                .map(parametro -> parametro.substring("X-Amz-Date=".length())).findFirst().orElseThrow();
        Instant expiraEm = java.time.ZonedDateTime.parse(dataAssinatura,
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX"))
                .toInstant().plus(configuracao.linkTtlHoras(), ChronoUnit.HOURS);
        return new ExportacaoConcluida(pedido.exportacaoId(), pedido.tipo(), pedido.email(), pedido.nome(), nomeArquivo, url, expiraEm);
    }

    private void garantirBucket() throws Exception {
        if (!minioInterno.bucketExists(BucketExistsArgs.builder().bucket(configuracao.exportBucket()).build())) {
            try {
                minioInterno.makeBucket(MakeBucketArgs.builder().bucket(configuracao.exportBucket()).build());
            } catch (io.minio.errors.ErrorResponseException erro) {
                if (!"BucketAlreadyOwnedByYou".equals(erro.errorResponse().code())) throw erro;
            }
        }
    }
}
