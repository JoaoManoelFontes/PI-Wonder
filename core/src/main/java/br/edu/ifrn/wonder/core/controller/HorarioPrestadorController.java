package br.edu.ifrn.wonder.core.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.CadastroHorarioAtendimentoRequest;
import br.edu.ifrn.wonder.core.dto.HorarioAtendimentoPublicResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.HorarioAtendimentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo/prestadores/{prestadorId}/horarios")
public class HorarioPrestadorController {

    private final HorarioAtendimentoService horarioAtendimentoService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    HorarioAtendimentoPublicResponse cadastrar(@PathVariable("prestadorId") Long prestadorId,
            @Valid @RequestBody CadastroHorarioAtendimentoRequest dados) {
        return HorarioAtendimentoPublicResponse.from(horarioAtendimentoService.cadastrar(
                prestadorId, usuarioAutenticadoProvider.getUuidUsuario(), dados));
    }

    @DeleteMapping("/{horarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(@PathVariable("prestadorId") Long prestadorId, @PathVariable("horarioId") Long horarioId) {
        horarioAtendimentoService.remover(prestadorId, horarioId, usuarioAutenticadoProvider.getUuidUsuario());
    }
}
