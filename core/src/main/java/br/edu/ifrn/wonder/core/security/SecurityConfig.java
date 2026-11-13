package br.edu.ifrn.wonder.core.security;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entrada = (pedido, resposta, excecao) -> {
            if (excecao instanceof AuthenticationServiceException) {
                resposta.setStatus(503);
                resposta.setCharacterEncoding("UTF-8");
                resposta.setContentType("application/problem+json");
                resposta.getWriter().write("{\"status\":503,\"detail\":\"Autenticação temporariamente indisponível\"}");
            } else {
                new BearerTokenAuthenticationEntryPoint().commence(pedido, resposta, excecao);
            }
        };
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/catalogo/prestadores/me", "/catalogo/prestadores/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/catalogo/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/agendamentos/dias-disponiveis",
                                "/agendamentos/disponibilidade").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> {
                    oauth2.opaqueToken(Customizer.withDefaults()).authenticationEntryPoint(entrada);
                    oauth2.addObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                        @Override
                        public <O extends BearerTokenAuthenticationFilter> O postProcess(O filtro) {
                            var falha = new AuthenticationEntryPointFailureHandler(entrada);
                            falha.setRethrowAuthenticationServiceException(false);
                            filtro.setAuthenticationFailureHandler(falha);
                            return filtro;
                        }
                    });
                })
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${wonder.cors.allowed-origins:}") List<String> origensPermitidas) {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(origensPermitidas);
        configuracao.setAllowedMethods(List.of("*"));
        configuracao.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", configuracao);
        return fonte;
    }

}
