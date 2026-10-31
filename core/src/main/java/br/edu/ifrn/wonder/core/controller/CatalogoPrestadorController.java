package br.edu.ifrn.wonder.core.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.AvaliacaoPublicResponse;
import br.edu.ifrn.wonder.core.dto.PrestadorPublicResponse;
import br.edu.ifrn.wonder.core.dto.HorarioAtendimentoPublicResponse;
import br.edu.ifrn.wonder.core.dto.CategoriaPublicResponse;
import br.edu.ifrn.wonder.core.dto.ServicoPublicResponse;
import br.edu.ifrn.wonder.core.service.CatalogoPublicoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo/prestadores")
public class CatalogoPrestadorController {

    private final CatalogoPublicoService catalogoPublicoService;

    @GetMapping("/{prestadorId}")
    PrestadorPublicResponse buscarDetalhe(@PathVariable("prestadorId") Long prestadorId) {
        return PrestadorPublicResponse.from(catalogoPublicoService.buscarPrestadorPublico(prestadorId));
    }

    @GetMapping("/{prestadorId}/horarios")
    List<HorarioAtendimentoPublicResponse> listarHorarios(@PathVariable("prestadorId") Long prestadorId) {
        return catalogoPublicoService.listarHorariosDoPrestador(prestadorId)
                .stream()
                .map(HorarioAtendimentoPublicResponse::from)
                .toList();
    }

    @GetMapping("/{prestadorId}/categorias")
    List<CategoriaPublicResponse> listarCategorias(@PathVariable("prestadorId") Long prestadorId) {
        return catalogoPublicoService.listarCategoriasDoPrestador(prestadorId)
                .stream()
                .map(CategoriaPublicResponse::from)
                .toList();
    }

    @GetMapping("/{prestadorId}/servicos")
    List<ServicoPublicResponse> listarServicos(@PathVariable("prestadorId") Long prestadorId) {
        return catalogoPublicoService.listarServicosDoPrestador(prestadorId)
                .stream()
                .map(ServicoPublicResponse::from)
                .toList();
    }

    @GetMapping("/{prestadorId}/avaliacoes")
    List<AvaliacaoPublicResponse> listarAvaliacoes(@PathVariable("prestadorId") Long prestadorId) {
        return catalogoPublicoService.listarAvaliacoesDoPrestador(prestadorId)
                .stream()
                .map(AvaliacaoPublicResponse::from)
                .toList();
    }
}
