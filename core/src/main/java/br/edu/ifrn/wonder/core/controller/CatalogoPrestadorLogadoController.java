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

import br.edu.ifrn.wonder.core.dto.PrestadorProprioResponse;
import br.edu.ifrn.wonder.core.dto.CategoriaPublicResponse;
import br.edu.ifrn.wonder.core.dto.HorarioAtendimentoPublicResponse;
import br.edu.ifrn.wonder.core.dto.ServicoPublicResponse;
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
    PrestadorProprioResponse buscarMeuCadastro() {
        return PrestadorProprioResponse.from(prestadorService.buscarPrestadorDoUsuario(
                usuarioAutenticadoProvider.getUuidUsuario()));
    }

    @GetMapping("/me/categorias")
    List<CategoriaPublicResponse> listarMinhasCategorias() {
        return prestadorService.listarCategoriasDoUsuario(usuarioAutenticadoProvider.getUuidUsuario())
                .stream()
                .map(CategoriaPublicResponse::from)
                .toList();
    }

    @GetMapping("/me/servicos")
    List<ServicoPublicResponse> listarMeusServicos() {
        return prestadorService.listarServicosDoUsuario(usuarioAutenticadoProvider.getUuidUsuario())
                .stream()
                .map(ServicoPublicResponse::from)
                .toList();
    }

    @GetMapping("/me/horarios")
    List<HorarioAtendimentoPublicResponse> listarMeusHorarios() {
        return prestadorService.listarHorariosDoUsuario(usuarioAutenticadoProvider.getUuidUsuario())
                .stream()
                .map(HorarioAtendimentoPublicResponse::from)
                .toList();
    }

    @PutMapping("/{prestadorId}")
    PrestadorProprioResponse atualizarCadastro(
            @PathVariable("prestadorId") Long prestadorId,
            @Valid @RequestBody AtualizacaoPrestadorRequest request) {
        return PrestadorProprioResponse.from(prestadorService.atualizarPrestador(
                prestadorId, usuarioAutenticadoProvider.getUuidUsuario(), request));
    }

    @PostMapping("/{prestadorId}/enviar-aprovacao")
    PrestadorProprioResponse enviarParaAprovacao(@PathVariable("prestadorId") Long prestadorId) {
        return PrestadorProprioResponse.from(prestadorService.enviarParaAprovacao(
                prestadorId, usuarioAutenticadoProvider.getUuidUsuario()));
    }

    @DeleteMapping("/{prestadorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void inativar(@PathVariable("prestadorId") Long prestadorId) {
        prestadorService.inativar(prestadorId, usuarioAutenticadoProvider.getUuidUsuario());
    }
}
