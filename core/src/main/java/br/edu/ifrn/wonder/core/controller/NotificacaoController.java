package br.edu.ifrn.wonder.core.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.NotificacaoResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.NotificacaoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @GetMapping
    List<NotificacaoResponse> listar(@RequestParam(value = "status", required = false) String status) {
        return notificacaoService.listar(usuarioAutenticadoProvider.getUuidUsuario(), status)
                .stream().map(NotificacaoResponse::from).toList();
    }

    @PatchMapping("/{id}/lida")
    NotificacaoResponse marcarComoLida(@PathVariable("id") Long id) {
        return NotificacaoResponse.from(notificacaoService.marcarComoLida(
                id, usuarioAutenticadoProvider.getUuidUsuario()));
    }
}
