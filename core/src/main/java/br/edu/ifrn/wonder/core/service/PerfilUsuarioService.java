package br.edu.ifrn.wonder.core.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerfilUsuarioService {

    private final PerfilUsuarioRepository perfilUsuarioRepository;

    public PerfilUsuario buscarOuCriarPorKeycloakId(UUID keycloakId) {
        return perfilUsuarioRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> criarPerfilUsuario(keycloakId));
    }

    private PerfilUsuario criarPerfilUsuario(UUID keycloakId) {
        try {
            return perfilUsuarioRepository.saveAndFlush(new PerfilUsuario(keycloakId));
        } catch (DataIntegrityViolationException exception) {
            return perfilUsuarioRepository.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> exception);
        }
    }
}
