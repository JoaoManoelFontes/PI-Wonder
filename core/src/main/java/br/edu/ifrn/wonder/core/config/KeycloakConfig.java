package br.edu.ifrn.wonder.core.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakConfig {

    @Bean
    RestClient keycloakRestClient(KeycloakProperties propriedades) {
        HttpClient clienteHttp = HttpClient.newBuilder()
                .connectTimeout(propriedades.connectTimeout())
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteHttp);
        fabrica.setReadTimeout(propriedades.readTimeout());

        return RestClient.builder()
                .baseUrl(propriedades.serverUrl())
                .requestFactory(fabrica)
                .build();
    }
}
