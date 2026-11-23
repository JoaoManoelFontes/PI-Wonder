package br.edu.ifrn.wonder.worker.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MinioPropertiesTest {
    @ParameterizedTest
    @ValueSource(ints = {0, -1, 169})
    void rejeitaTtlForaDosLimites(int horas) {
        assertThatThrownBy(() -> new MinioProperties("http://localhost:9000", "http://localhost:9000", "", "", "bucket", horas))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("wonder.minio.link-ttl-horas");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 24, 168})
    void aceitaTtlNosLimites(int horas) {
        assertThat(new MinioProperties("http://localhost:9000", "http://localhost:9000", "", "", "bucket", horas).linkTtlHoras()).isEqualTo(horas);
    }
}
