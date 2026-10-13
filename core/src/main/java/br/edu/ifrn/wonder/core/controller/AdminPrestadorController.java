package br.edu.ifrn.wonder.core.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.PrestadorSolicitacaoDetalheResponse;
import br.edu.ifrn.wonder.core.dto.PrestadorSolicitacaoResumoResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.PrestadorService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/prestadores/solicitacoes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPrestadorController {

    private final PrestadorService prestadorService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @GetMapping
    List<PrestadorSolicitacaoResumoResponse> listarSolicitacoes() {
        return prestadorService.listarSolicitacoesPendentes()
                .stream()
                .map(PrestadorSolicitacaoResumoResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    PrestadorSolicitacaoDetalheResponse detalharSolicitacao(@PathVariable Long id) {
        return PrestadorSolicitacaoDetalheResponse.from(
                prestadorService.detalharSolicitacaoPendente(id)
        );
    }

    @PostMapping("/{id}/aprovar")
    PrestadorSolicitacaoDetalheResponse aprovarSolicitacao(@PathVariable Long id) {
        return PrestadorSolicitacaoDetalheResponse.from(
                prestadorService.aprovarSolicitacao(id, usuarioAutenticadoProvider.getUuidUsuario())
        );
    }

    @PostMapping("/{id}/reprovar")
    PrestadorSolicitacaoDetalheResponse reprovarSolicitacao(@PathVariable Long id) {
        return PrestadorSolicitacaoDetalheResponse.from(
                prestadorService.reprovarSolicitacao(id, usuarioAutenticadoProvider.getUuidUsuario())
        );
    }
}
