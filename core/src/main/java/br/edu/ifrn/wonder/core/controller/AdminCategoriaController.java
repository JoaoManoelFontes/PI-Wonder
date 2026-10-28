package br.edu.ifrn.wonder.core.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.CategoriaPublicResponse;
import br.edu.ifrn.wonder.core.service.CategoriaService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/categorias")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    List<CategoriaPublicResponse> listarCategorias() {
        return categoriaService.listarCategorias().stream()
                .map(CategoriaPublicResponse::from)
                .toList();
    }
}
