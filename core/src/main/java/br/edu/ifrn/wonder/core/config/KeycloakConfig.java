package br.edu.ifrn.wonder.core.config;

import java.net.http.HttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;

import br.edu.ifrn.wonder.core.security.KeycloakTokenIntrospector;

@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakConfig {

    @Bean
    RestClient keycloakRestClient(KeycloakProperties propriedades) {
        return RestClient.builder()
                .baseUrl(propriedades.serverUrl())
                .requestFactory(fabricaHttp(propriedades))
                .build();
    }

    @Bean
    OpaqueTokenIntrospector keycloakTokenIntrospector(KeycloakProperties propriedades) {
        RestTemplate cliente = new RestTemplate(fabricaHttp(propriedades));
        cliente.getInterceptors().add(new BasicAuthenticationInterceptor(
                URLEncoder.encode(propriedades.clientId(), StandardCharsets.UTF_8),
                URLEncoder.encode(propriedades.clientSecret(), StandardCharsets.UTF_8)));
        String endereco = propriedades.serverUrl().replaceAll("/+$", "")
                + "/realms/" + propriedades.realm() + "/protocol/openid-connect/token/introspect";
        return new KeycloakTokenIntrospector(new SpringOpaqueTokenIntrospector(endereco, cliente));
    }

    private JdkClientHttpRequestFactory fabricaHttp(KeycloakProperties propriedades) {
        HttpClient clienteHttp = HttpClient.newBuilder()
                .connectTimeout(propriedades.connectTimeout())
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteHttp);
        fabrica.setReadTimeout(propriedades.readTimeout());

        return fabrica;
    }
}
