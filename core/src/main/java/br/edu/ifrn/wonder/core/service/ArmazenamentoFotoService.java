package br.edu.ifrn.wonder.core.service;

import java.io.InputStream;

import br.edu.ifrn.wonder.core.config.MinioProperties;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ArmazenamentoFotoService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    private volatile boolean bucketDisponivel;

    public void salvar(String nomeObjeto, MultipartFile arquivo) {
        try {
            garantirBucket();

            try (InputStream conteudo = arquivo.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.bucket())
                        .object(nomeObjeto)
                        .contentType(arquivo.getContentType())
                        .stream(conteudo, arquivo.getSize(), -1L)
                        .build());
            }
        } catch (Exception exception) {
            throw indisponibilidade(exception);
        }
    }

    public FotoPerfilConteudo buscar(String nomeObjeto) {
        try (GetObjectResponse resposta = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(nomeObjeto)
                .build())) {
            return new FotoPerfilConteudo(
                    resposta.readAllBytes(),
                    resposta.headers().get("Content-Type")
            );
        } catch (Exception exception) {
            throw indisponibilidade(exception);
        }
    }

    private synchronized void garantirBucket() throws Exception {
        if (bucketDisponivel) {
            return;
        }

        boolean existe = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.bucket())
                .build());

        if (!existe) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(properties.bucket())
                    .build());
        }

        bucketDisponivel = true;
    }

    private ResponseStatusException indisponibilidade(Exception cause) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "O armazenamento de fotos está indisponível",
                cause
        );
    }
}
