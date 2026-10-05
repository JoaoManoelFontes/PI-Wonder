package br.edu.ifrn.wonder.core.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.dto.PerfilUsuarioResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.PerfilUsuarioService;

@RestController
@RequiredArgsConstructor
public class PerfilUsuarioController {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final PerfilUsuarioService perfilUsuarioService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    PerfilUsuarioResponse buscarMeuPerfil() {
        PerfilUsuario perfilUsuario = perfilUsuarioService.buscarOuCriarPorKeycloakId(
                usuarioAutenticadoProvider.getUuidUsuario()
        );

        return PerfilUsuarioResponse.from(perfilUsuario);
    }
}
