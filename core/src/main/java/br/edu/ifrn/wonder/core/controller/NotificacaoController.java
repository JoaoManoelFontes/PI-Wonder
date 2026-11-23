package br.edu.ifrn.wonder.core.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import br.edu.ifrn.wonder.core.dto.NotificacaoResponse;
import br.edu.ifrn.wonder.core.dto.NotificacoesPaginaResponse;
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

    @GetMapping("/pagina")
    NotificacoesPaginaResponse listarPagina(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pagina", defaultValue = "0") int pagina,
            @RequestParam(value = "limite", defaultValue = "20") int limite) {
        return notificacaoService.listarPagina(
                usuarioAutenticadoProvider.getUuidUsuario(), status, pagina, limite);
    }

    @PatchMapping("/{id}/lida")
    NotificacaoResponse marcarComoLida(@PathVariable("id") Long id) {
        return NotificacaoResponse.from(notificacaoService.marcarComoLida(
                id, usuarioAutenticadoProvider.getUuidUsuario()));
    }

    @PatchMapping("/lidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void marcarTodasComoLidas() {
        notificacaoService.marcarTodasComoLidas(usuarioAutenticadoProvider.getUuidUsuario());
    }
}
