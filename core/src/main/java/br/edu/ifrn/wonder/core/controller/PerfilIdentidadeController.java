package br.edu.ifrn.wonder.core.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.AtualizacaoUsuarioAdminRequest;
import br.edu.ifrn.wonder.core.dto.UsuarioAdminResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.UsuarioAdminService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/perfis/eu/identidade")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')")
public class PerfilIdentidadeController {
    private final UsuarioAdminService usuarioService;
    private final UsuarioAutenticadoProvider usuarioAutenticado;

    @GetMapping
    IdentidadeResponse consultar() {
        return IdentidadeResponse.from(usuarioService.detalhar(usuarioAutenticado.getUuidUsuario()));
    }

    @PutMapping
    IdentidadeResponse atualizar(@Valid @RequestBody AtualizacaoUsuarioAdminRequest pedido) {
        return IdentidadeResponse.from(usuarioService.atualizar(usuarioAutenticado.getUuidUsuario(), pedido));
    }

    public record IdentidadeResponse(UUID id, String nome, String sobrenome, String email) {
        static IdentidadeResponse from(UsuarioAdminResponse usuario) {
            return new IdentidadeResponse(usuario.id(), usuario.nome(), usuario.sobrenome(), usuario.email());
        }
    }
}
