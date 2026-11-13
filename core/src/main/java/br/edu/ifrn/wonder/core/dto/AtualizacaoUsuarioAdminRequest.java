package br.edu.ifrn.wonder.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizacaoUsuarioAdminRequest(
        @NotBlank @Size(max = 255) String nome,
        @Size(max = 255) String sobrenome,
        @NotBlank @Email @Size(max = 254) String email) {
    public AtualizacaoUsuarioAdminRequest {
        nome = nome == null ? null : nome.trim();
        sobrenome = sobrenome == null ? "" : sobrenome.trim();
        email = email == null ? null : email.trim();
    }
}
