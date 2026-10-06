package br.edu.ifrn.wonder.core.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.dto.CadastroPrestadorRequest;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrestadorService {

    private final PerfilUsuarioService perfilUsuarioService;
    private final PrestadorRepository prestadorRepository;

    @Transactional
    public Prestador cadastrarPrestador(UUID keycloakId, CadastroPrestadorRequest request) {
        PerfilUsuario perfilUsuario = perfilUsuarioService.buscarOuCriarPorKeycloakId(keycloakId);

        prestadorRepository.findByPerfilUsuarioId(perfilUsuario.getId())
                .ifPresent(prestador -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Usuário já possui cadastro de prestador");
                });

        Prestador prestador = Prestador.builder()
                .perfilUsuario(perfilUsuario)
                .nomeEstab(request.nomeEstab())
                .documento(request.documento())
                .endereco(request.endereco())
                .numero(request.numero())
                .bairro(request.bairro())
                .cidade(request.cidade())
                .estado(request.estado())
                .complemento(request.complemento())
                .fotoUrl(request.fotoUrl())
                .status(StatusPrestador.PENDENTE)
                .ativo(false)
                .enviadoEm(LocalDateTime.now())
                .build();

        return prestadorRepository.save(prestador);
    }
}
