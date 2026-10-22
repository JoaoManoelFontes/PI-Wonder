package br.edu.ifrn.wonder.core.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.AvaliacaoPublicResponse;
import br.edu.ifrn.wonder.core.dto.CadastroAvaliacaoRequest;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.AvaliacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo/prestadores/{prestadorId}/avaliacoes")
public class AvaliacaoPrestadorController {

    private final AvaliacaoService avaliacaoService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AvaliacaoPublicResponse cadastrar(@PathVariable("prestadorId") Long prestadorId,
            @Valid @RequestBody CadastroAvaliacaoRequest dados) {
        return AvaliacaoPublicResponse.from(avaliacaoService.cadastrar(
                prestadorId, usuarioAutenticadoProvider.getUuidUsuario(), dados));
    }
}
