package br.edu.ifrn.wonder.core.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.PrestadorPublicResponse;
import br.edu.ifrn.wonder.core.dto.CategoriaPublicResponse;
import br.edu.ifrn.wonder.core.dto.AtualizacaoPrestadorRequest;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.PrestadorService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo/prestadores")
public class CatalogoPrestadorLogadoController {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final PrestadorService prestadorService;

    @GetMapping("/me")
    PrestadorPublicResponse buscarMeuCadastro() {
        return PrestadorPublicResponse.from(prestadorService.buscarPrestadorDoUsuario(
                usuarioAutenticadoProvider.getUuidUsuario()));
    }

    @GetMapping("/me/categorias")
    List<CategoriaPublicResponse> listarMinhasCategorias() {
        return prestadorService.listarCategoriasDoUsuario(usuarioAutenticadoProvider.getUuidUsuario())
                .stream()
                .map(CategoriaPublicResponse::from)
                .toList();
    }

    @PutMapping("/{prestadorId}")
    PrestadorPublicResponse atualizarCadastro(
            @PathVariable("prestadorId") Long prestadorId,
            @Valid @RequestBody AtualizacaoPrestadorRequest request) {
        return PrestadorPublicResponse.from(prestadorService.atualizarPrestador(
                prestadorId, usuarioAutenticadoProvider.getUuidUsuario(), request));
    }

    @PostMapping("/{prestadorId}/enviar-aprovacao")
    PrestadorPublicResponse enviarParaAprovacao(@PathVariable("prestadorId") Long prestadorId) {
        return PrestadorPublicResponse.from(prestadorService.enviarParaAprovacao(
                prestadorId, usuarioAutenticadoProvider.getUuidUsuario()));
    }

    @DeleteMapping("/{prestadorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void inativar(@PathVariable("prestadorId") Long prestadorId) {
        prestadorService.inativar(prestadorId, usuarioAutenticadoProvider.getUuidUsuario());
    }
}
