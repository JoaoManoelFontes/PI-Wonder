package br.edu.ifrn.wonder.core.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.PrestadorPublicResponse;
import br.edu.ifrn.wonder.core.service.PrestadorService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo/prestadores")
public class CatalogoPrestadorController {

    private final PrestadorService prestadorService;

    @GetMapping("/{prestadorId}")
    PrestadorPublicResponse buscarDetalhe(@PathVariable("prestadorId") Long prestadorId) {
        return PrestadorPublicResponse.from(prestadorService.buscarDetalhePublico(prestadorId));
    }
}
