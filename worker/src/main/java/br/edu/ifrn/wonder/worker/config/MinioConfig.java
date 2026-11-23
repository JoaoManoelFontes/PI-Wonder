package br.edu.ifrn.wonder.worker.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {
    @Bean
    MinioClient minioInterno(MinioProperties configuracao) {
        return MinioClient.builder().endpoint(configuracao.endpoint())
                .credentials(configuracao.accessKey(), configuracao.secretKey()).build();
    }

    @Bean
    MinioClient minioPublico(MinioProperties configuracao) {
        // Região explícita permite assinar sem consultar o endpoint externo pela rede interna.
        return MinioClient.builder().endpoint(configuracao.publicEndpoint()).region("us-east-1")
                .credentials(configuracao.accessKey(), configuracao.secretKey()).build();
    }
}
