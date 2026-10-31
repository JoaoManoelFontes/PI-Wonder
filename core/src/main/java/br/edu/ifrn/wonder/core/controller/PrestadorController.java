package br.edu.ifrn.wonder.core.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.dto.CadastroPrestadorRequest;
import br.edu.ifrn.wonder.core.dto.PrestadorPublicResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.PrestadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo/prestadores")
public class PrestadorController {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final PrestadorService prestadorService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PrestadorPublicResponse cadastrarPrestador(
            @Valid @ModelAttribute CadastroPrestadorRequest request,
            @RequestPart(value = "arquivo", required = false) MultipartFile arquivo) {
        Prestador prestador = prestadorService.cadastrarPrestador(
                usuarioAutenticadoProvider.getUuidUsuario(),
                request,
                arquivo
        );

        return PrestadorPublicResponse.from(prestador);
    }
}
