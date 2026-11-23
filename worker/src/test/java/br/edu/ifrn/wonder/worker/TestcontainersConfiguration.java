package br.edu.ifrn.wonder.worker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));
	}

	@Bean(initMethod = "start", destroyMethod = "stop")
	GenericContainer<?> minioContainer() {
		return new GenericContainer<>(DockerImageName.parse("bitnamilegacy/minio:2025.7.23"))
				.withEnv("MINIO_ROOT_USER", "wonder-test")
				.withEnv("MINIO_ROOT_PASSWORD", java.util.UUID.randomUUID().toString())
				.withExposedPorts(9000)
				.waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/minio/health/live").forPort(9000));
	}

	@Bean
	DynamicPropertyRegistrar minioProperties(@org.springframework.beans.factory.annotation.Qualifier("minioContainer") GenericContainer<?> minioContainer) {
		return registro -> {
			registro.add("wonder.minio.endpoint", () -> "http://127.0.0.1:" + minioContainer.getMappedPort(9000));
			registro.add("wonder.minio.public-endpoint", () -> "http://localhost:" + minioContainer.getMappedPort(9000));
			registro.add("wonder.minio.access-key", () -> minioContainer.getEnvMap().get("MINIO_ROOT_USER"));
			registro.add("wonder.minio.secret-key", () -> minioContainer.getEnvMap().get("MINIO_ROOT_PASSWORD"));
		};
	}

}
