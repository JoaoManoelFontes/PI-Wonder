package br.edu.ifrn.wonder.core.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerfilUsuarioService {

    private static final long TAMANHO_MAXIMO_FOTO = 5 * 1024 * 1024;
    private static final Set<String> TIPOS_FOTO_PERMITIDOS = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final PerfilUsuarioRepository perfilUsuarioRepository;
    private final ArmazenamentoFotoService armazenamentoFotoService;

    public PerfilUsuario buscarOuCriarPorKeycloakId(UUID keycloakId) {
        return perfilUsuarioRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> criarPerfilUsuario(keycloakId));
    }

    public PerfilUsuario atualizar(UUID keycloakId, MultipartFile arquivo, String numeroTelefone) {
        boolean fotoInformada = arquivo != null && !arquivo.isEmpty();
        if (fotoInformada) {
            validarFoto(arquivo);
        }

        PerfilUsuario perfilUsuario = buscarOuCriarPorKeycloakId(keycloakId);
        boolean perfilAlterado = false;

        if (fotoInformada) {
            String nomeObjeto = "perfis/%s/foto".formatted(perfilUsuario.getId());

            armazenamentoFotoService.salvar(nomeObjeto, arquivo);
            perfilUsuario.setFotoUrl(nomeObjeto);
            perfilAlterado = true;
        }

        if (numeroTelefone != null && !numeroTelefone.isBlank()) {
            perfilUsuario.setNumeroTelefone(numeroTelefone);
            perfilAlterado = true;
        }

        return perfilAlterado ? perfilUsuarioRepository.save(perfilUsuario) : perfilUsuario;
    }

    public FotoPerfilConteudo buscarFoto(UUID perfilId) {
        PerfilUsuario perfilUsuario = perfilUsuarioRepository.findById(perfilId)
                .orElseThrow(() -> fotoNaoEncontrada());

        if (perfilUsuario.getFotoUrl() == null) {
            throw fotoNaoEncontrada();
        }

        return armazenamentoFotoService.buscar(perfilUsuario.getFotoUrl());
    }

    private PerfilUsuario criarPerfilUsuario(UUID keycloakId) {
        try {
            return perfilUsuarioRepository.saveAndFlush(new PerfilUsuario(keycloakId));
        } catch (DataIntegrityViolationException exception) {
            return perfilUsuarioRepository.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> exception);
        }
    }

    private void validarFoto(MultipartFile arquivo) {
        if (arquivo.getSize() > TAMANHO_MAXIMO_FOTO) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "A foto deve ter no máximo 5 MB");
        }

        if (!TIPOS_FOTO_PERMITIDOS.contains(arquivo.getContentType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A foto deve estar nos formatos JPEG, PNG ou WebP"
            );
        }
    }

    private ResponseStatusException fotoNaoEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto de perfil não encontrada");
    }
}
