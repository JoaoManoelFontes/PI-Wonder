package br.edu.ifrn.wonder.core.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.AtualizacaoServicoRequest;
import br.edu.ifrn.wonder.core.dto.CadastroServicoRequest;
import br.edu.ifrn.wonder.core.dto.ServicoPublicResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.ServicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo/prestadores/{prestadorId}/servicos")
public class ServicoPrestadorController {

    private final ServicoService servicoService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ServicoPublicResponse cadastrar(@PathVariable("prestadorId") Long prestadorId,
            @Valid @RequestBody CadastroServicoRequest dados) {
        return ServicoPublicResponse.from(servicoService.cadastrar(
                prestadorId, usuarioAutenticadoProvider.getUuidUsuario(), dados));
    }

    @PutMapping("/{servicoId}")
    ServicoPublicResponse atualizar(@PathVariable("prestadorId") Long prestadorId,
            @PathVariable("servicoId") Long servicoId, @Valid @RequestBody AtualizacaoServicoRequest dados) {
        return ServicoPublicResponse.from(servicoService.atualizar(
                prestadorId, servicoId, usuarioAutenticadoProvider.getUuidUsuario(), dados));
    }

    @DeleteMapping("/{servicoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void inativar(@PathVariable("prestadorId") Long prestadorId, @PathVariable("servicoId") Long servicoId) {
        servicoService.inativar(prestadorId, servicoId, usuarioAutenticadoProvider.getUuidUsuario());
    }
}
