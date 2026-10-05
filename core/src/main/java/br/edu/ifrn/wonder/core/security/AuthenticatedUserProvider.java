package br.edu.ifrn.wonder.core.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserProvider {

    public String getUserId() {
        return getJwt().getSubject();
    }

    public UUID getUserUuid() {
        try {
            return UUID.fromString(getUserId());
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid JWT subject", exception);
        }
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(getJwt().getClaimAsString("email"));
    }

    public Jwt getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken();
        }

        throw new AuthenticationCredentialsNotFoundException("Authenticated JWT not found");
    }
}
