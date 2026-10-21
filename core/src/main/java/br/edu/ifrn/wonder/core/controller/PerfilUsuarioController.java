package br.edu.ifrn.wonder.core.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.dto.PerfilUsuarioResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.PerfilUsuarioService;

@RestController
@RequestMapping("/perfis")
public class PerfilUsuarioController {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final PerfilUsuarioService perfilUsuarioService;
    private final String apiPublicUrl;

    public PerfilUsuarioController(
            UsuarioAutenticadoProvider usuarioAutenticadoProvider,
            PerfilUsuarioService perfilUsuarioService,
            @Value("${wonder.api.public-url}") String apiPublicUrl
    ) {
        this.usuarioAutenticadoProvider = usuarioAutenticadoProvider;
        this.perfilUsuarioService = perfilUsuarioService;
        this.apiPublicUrl = apiPublicUrl;
    }

    @GetMapping("/eu")
    PerfilUsuarioResponse getUserProfile() {
        PerfilUsuario perfilUsuario = perfilUsuarioService.buscarOuCriarPorKeycloakId(
                usuarioAutenticadoProvider.getUuidUsuario()
        );

        return PerfilUsuarioResponse.from(perfilUsuario, apiPublicUrl);
    }

    @PutMapping(value = "/eu", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PerfilUsuarioResponse atualizarPerfil(
            @RequestPart(value = "arquivo", required = false) MultipartFile arquivo,
            @RequestParam(value = "numeroTelefone", required = false) String numeroTelefone
    ) {
        PerfilUsuario perfilUsuario = perfilUsuarioService.atualizar(
                usuarioAutenticadoProvider.getUuidUsuario(),
                arquivo,
                numeroTelefone
        );

        return PerfilUsuarioResponse.from(perfilUsuario, apiPublicUrl);
    }

    @GetMapping("/{perfilId}/foto")
    ResponseEntity<byte[]> buscarFoto(@PathVariable("perfilId") UUID perfilId) {
        FotoPerfilConteudo foto = perfilUsuarioService.buscarFoto(perfilId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.tipoConteudo()))
                .contentLength(foto.dados().length)
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(foto.dados());
    }
}
