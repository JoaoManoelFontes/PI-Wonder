package br.edu.ifrn.wonder.core.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.edu.ifrn.wonder.core.dto.FotoEstabelecimentoResponse;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.FotoEstabelecimentoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo/prestadores/{prestadorId}/fotos")
public class FotoEstabelecimentoController {

    private final FotoEstabelecimentoService fotos;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @GetMapping
    List<FotoEstabelecimentoResponse> listar(@PathVariable("prestadorId") Long prestadorId, Authentication autenticacao) {
        return fotos.listar(prestadorId, usuarioId(autenticacao), administrador(autenticacao));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    FotoEstabelecimentoResponse adicionar(@PathVariable("prestadorId") Long prestadorId, @RequestPart("file") MultipartFile arquivo) {
        return fotos.adicionar(prestadorId, usuarioAutenticadoProvider.getUuidUsuario(), arquivo);
    }

    @DeleteMapping("/{fotoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(@PathVariable("prestadorId") Long prestadorId, @PathVariable("fotoId") Long fotoId) {
        fotos.remover(prestadorId, fotoId, usuarioAutenticadoProvider.getUuidUsuario());
    }

    @GetMapping("/{fotoId}")
    ResponseEntity<byte[]> buscar(@PathVariable("prestadorId") Long prestadorId, @PathVariable("fotoId") Long fotoId,
            Authentication autenticacao) {
        FotoPerfilConteudo foto = fotos.buscarFoto(prestadorId, fotoId,
                usuarioId(autenticacao), administrador(autenticacao));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.tipoConteudo()))
                .contentLength(foto.dados().length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(foto.dados());
    }

    private UUID usuarioId(Authentication autenticacao) {
        return autenticacao == null || autenticacao instanceof AnonymousAuthenticationToken
                ? null : usuarioAutenticadoProvider.getUuidUsuario();
    }

    private boolean administrador(Authentication autenticacao) {
        return autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(autoridade -> autoridade.getAuthority().equals("ROLE_ADMIN"));
    }
}
