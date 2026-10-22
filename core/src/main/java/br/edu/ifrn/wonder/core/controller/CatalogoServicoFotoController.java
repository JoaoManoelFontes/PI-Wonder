package br.edu.ifrn.wonder.core.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.dto.ServicoPublicResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.ServicoFotoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalogo/prestadores/{prestadorId}/servicos/{servicoId}/foto")
public class CatalogoServicoFotoController {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final ServicoFotoService servicoFotoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ServicoPublicResponse atualizarFoto(@PathVariable("prestadorId") Long prestadorId,
            @PathVariable("servicoId") Long servicoId, @RequestPart("file") MultipartFile arquivo) {
        return ServicoPublicResponse.from(servicoFotoService.atualizarFoto(
                prestadorId, servicoId, usuarioAutenticadoProvider.getUuidUsuario(), arquivo));
    }

    @GetMapping
    ResponseEntity<byte[]> buscarFoto(@PathVariable("prestadorId") Long prestadorId,
            @PathVariable("servicoId") Long servicoId, @AuthenticationPrincipal Jwt jwt,
            Authentication autenticacao) {
        UUID keycloakId = jwt == null ? null : usuarioAutenticadoProvider.getUuidUsuario();
        boolean administrador = autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(autoridade -> autoridade.getAuthority().equals("ROLE_ADMIN"));
        FotoPerfilConteudo foto = servicoFotoService.buscarFoto(prestadorId, servicoId, keycloakId, administrador);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.tipoConteudo()))
                .contentLength(foto.dados().length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(foto.dados());
    }
}
