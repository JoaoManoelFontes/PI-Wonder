package br.edu.ifrn.wonder.core.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.AtualizacaoStatusUsuarioRequest;
import br.edu.ifrn.wonder.core.dto.AtualizacaoUsuarioAdminRequest;
import br.edu.ifrn.wonder.core.dto.UsuarioAdminResponse;
import br.edu.ifrn.wonder.core.dto.UsuariosAdminPaginaResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.UsuarioAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsuarioController {
    private final UsuarioAdminService usuarioAdminService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @GetMapping
    UsuariosAdminPaginaResponse listar(@RequestParam(name = "busca", defaultValue = "") String busca,
            @RequestParam(name = "inicio", defaultValue = "0") int inicio,
            @RequestParam(name = "limite", defaultValue = "20") int limite) {
        return usuarioAdminService.listar(busca, inicio, limite);
    }

    @GetMapping("/{id}")
    UsuarioAdminResponse detalhar(@PathVariable("id") UUID id) {
        return usuarioAdminService.detalhar(id);
    }

    @PutMapping("/{id}")
    UsuarioAdminResponse atualizar(@PathVariable("id") UUID id, @Valid @RequestBody AtualizacaoUsuarioAdminRequest pedido) {
        return usuarioAdminService.atualizar(id, pedido);
    }

    @PatchMapping("/{id}/status")
    UsuarioAdminResponse atualizarStatus(@PathVariable("id") UUID id,
            @Valid @RequestBody AtualizacaoStatusUsuarioRequest pedido) {
        return usuarioAdminService.atualizarStatus(id, pedido.ativo(), usuarioAutenticadoProvider.getUuidUsuario());
    }
}
