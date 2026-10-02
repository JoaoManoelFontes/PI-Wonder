package br.edu.ifrn.wonder.core.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.security.AuthenticatedUserProvider;

@RestController
public class CustomerController {

    private final AuthenticatedUserProvider authenticatedUserProvider;

    public CustomerController(AuthenticatedUserProvider authenticatedUserProvider) {
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @GetMapping("/me")
    Map<String, Object> me() {
        Jwt jwt = authenticatedUserProvider.getJwt();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", authenticatedUserProvider.getUserId());
        response.put("email", authenticatedUserProvider.getEmail().orElse(null));
        response.put("headers", jwt.getHeaders());
        response.put("claims", jwt.getClaims());

        return response;
    }
}
