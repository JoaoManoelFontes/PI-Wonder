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
public class UsuarioAutenticadoProvider {

    public String getIdUsuario() {
        return getJwt().getSubject();
    }

    public UUID getUuidUsuario() {
        try {
            return UUID.fromString(getIdUsuario());
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException("O identificador do usuário no JWT é inválido", exception);
        }
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(getJwt().getClaimAsString("email"));
    }

    public Jwt getJwt() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao instanceof JwtAuthenticationToken autenticacaoJwt) {
            return autenticacaoJwt.getToken();
        }

        throw new AuthenticationCredentialsNotFoundException("JWT do usuário autenticado não encontrado");
    }
}
