package br.edu.ifrn.wonder.core.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.edu.ifrn.wonder.core.dto.CategoriaPublicResponse;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.service.CategoriaFotoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminCategoriaFotoController {

    private final CategoriaFotoService categoriaFotoService;

    @PostMapping(path = {
            "/admin/categorias/{categoriaId}/foto",
            "/catalogo/admin/categorias/{categoriaId}/foto"
    }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    CategoriaPublicResponse atualizarFoto(@PathVariable("categoriaId") Long categoriaId,
            @RequestPart("file") MultipartFile arquivo) {
        return CategoriaPublicResponse.from(categoriaFotoService.atualizarFoto(categoriaId, arquivo));
    }

    @GetMapping("/catalogo/categorias/{categoriaId}/foto")
    ResponseEntity<byte[]> buscarFoto(@PathVariable("categoriaId") Long categoriaId) {
        FotoPerfilConteudo foto = categoriaFotoService.buscarFoto(categoriaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.tipoConteudo()))
                .contentLength(foto.dados().length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(foto.dados());
    }
}
