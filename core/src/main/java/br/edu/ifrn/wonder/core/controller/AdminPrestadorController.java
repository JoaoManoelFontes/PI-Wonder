package br.edu.ifrn.wonder.core.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.dto.AtualizacaoStatusPrestadorRequest;
import br.edu.ifrn.wonder.core.dto.PrestadorAdminDetalheResponse;
import br.edu.ifrn.wonder.core.dto.PrestadorAdminResumoResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.PrestadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/prestadores")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPrestadorController {

    private final PrestadorService prestadorService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @GetMapping("/pendentes")
    List<PrestadorAdminResumoResponse> listarPendentes() {
        return prestadorService.listarSolicitacoesPendentes()
                .stream()
                .map(PrestadorAdminResumoResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    PrestadorAdminDetalheResponse detalharPrestador(@PathVariable("id") Long id) {
        return montarDetalhe(prestadorService.detalharPrestadorAdmin(id));
    }

    @PatchMapping("/{id}/status")
    PrestadorAdminDetalheResponse atualizarStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody AtualizacaoStatusPrestadorRequest request) {
        Prestador prestador = prestadorService.atualizarStatusAdmin(
                id,
                request,
                usuarioAutenticadoProvider.getUuidUsuario()
        );
        return montarDetalhe(prestador);
    }

    private PrestadorAdminDetalheResponse montarDetalhe(Prestador prestador) {
        return PrestadorAdminDetalheResponse.from(
                prestador,
                prestadorService.listarServicosAdmin(prestador.getId()),
                prestadorService.listarHorariosAdmin(prestador.getId())
        );
    }
}
