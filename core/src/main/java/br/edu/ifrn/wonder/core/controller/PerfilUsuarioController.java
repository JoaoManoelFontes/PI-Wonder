package br.edu.ifrn.wonder.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.dto.PerfilUsuarioResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.PerfilUsuarioService;

@RestController
public class PerfilUsuarioController {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final PerfilUsuarioService perfilUsuarioService;

    public PerfilUsuarioController(
            UsuarioAutenticadoProvider usuarioAutenticadoProvider,
            PerfilUsuarioService perfilUsuarioService
    ) {
        this.usuarioAutenticadoProvider = usuarioAutenticadoProvider;
        this.perfilUsuarioService = perfilUsuarioService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    PerfilUsuarioResponse buscarMeuPerfil() {
        PerfilUsuario perfilUsuario = perfilUsuarioService.buscarOuCriarPorKeycloakId(
                usuarioAutenticadoProvider.getUuidUsuario()
        );

        return PerfilUsuarioResponse.from(perfilUsuario);
    }
}
