package br.edu.ifrn.wonder.core.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.dto.CadastroPrestadorRequest;
import br.edu.ifrn.wonder.core.dto.PrestadorPublicResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.PrestadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/prestadores")
public class PrestadorController {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final PrestadorService prestadorService;

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    PrestadorPublicResponse cadastrarPrestador(@Valid @RequestBody CadastroPrestadorRequest request) {
        Prestador prestador = prestadorService.cadastrarPrestador(
                usuarioAutenticadoProvider.getUuidUsuario(),
                request
        );

        return PrestadorPublicResponse.from(prestador);
    }
}
