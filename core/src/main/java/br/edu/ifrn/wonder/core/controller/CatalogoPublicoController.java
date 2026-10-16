package br.edu.ifrn.wonder.core.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.CategoriaPublicResponse;
import br.edu.ifrn.wonder.core.dto.PrestadorPublicResponse;
import br.edu.ifrn.wonder.core.service.CatalogoPublicoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo")
public class CatalogoPublicoController {

    private final CatalogoPublicoService catalogoPublicoService;

    @GetMapping("/categorias")
    List<CategoriaPublicResponse> listarCategorias() {
        return catalogoPublicoService.listarCategoriasAtivas()
                .stream()
                .map(CategoriaPublicResponse::from)
                .toList();
    }

    @GetMapping("/prestadores")
    List<PrestadorPublicResponse> listarPrestadores(
            @RequestParam(name = "nome", required = false) String nome,
            @RequestParam(name = "categoria_id", required = false) Long categoriaId) {
        return catalogoPublicoService.listarPrestadoresPublicos(nome, categoriaId)
                .stream()
                .map(PrestadorPublicResponse::from)
                .toList();
    }
}
