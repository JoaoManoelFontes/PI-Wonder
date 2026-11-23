package br.edu.ifrn.wonder.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wonder.minio")
public record MinioProperties(String endpoint, String publicEndpoint, String accessKey, String secretKey,
        String exportBucket, int linkTtlHoras) {
    public MinioProperties {
        if (linkTtlHoras < 1 || linkTtlHoras > 168) {
            throw new IllegalArgumentException("wonder.minio.link-ttl-horas deve estar entre 1 e 168");
        }
    }
}
