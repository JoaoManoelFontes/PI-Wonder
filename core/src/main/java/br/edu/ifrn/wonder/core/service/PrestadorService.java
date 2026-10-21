package br.edu.ifrn.wonder.core.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.dto.CadastroPrestadorRequest;
import br.edu.ifrn.wonder.core.dto.AtualizacaoPrestadorRequest;
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

    @Transactional(readOnly = true)
    public Prestador buscarPrestadorDoUsuario(UUID keycloakId) {
        return prestadorRepository.findByPerfilUsuarioKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
    }

    @Transactional
    public Prestador atualizarPrestador(Long prestadorId, UUID keycloakId, AtualizacaoPrestadorRequest request) {
        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));

        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para modificar este prestador");
        }
        if (prestador.getStatus() == StatusPrestador.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro pendente de análise não pode ser editado");
        }

        if (request.nomeEstab() != null) {
            prestador.setNomeEstab(request.nomeEstab());
        }
        if (request.documento() != null) {
            prestador.setDocumento(request.documento());
        }
        if (request.endereco() != null) {
            prestador.setEndereco(request.endereco());
        }
        if (request.numero() != null) {
            prestador.setNumero(request.numero());
        }
        if (request.bairro() != null) {
            prestador.setBairro(request.bairro());
        }
        if (request.cidade() != null) {
            prestador.setCidade(request.cidade());
        }
        if (request.estado() != null) {
            prestador.setEstado(request.estado());
        }
        if (request.complemento() != null) {
            prestador.setComplemento(request.complemento());
        }

        return prestador;
    }

    @Transactional(readOnly = true)
    public List<Prestador> listarSolicitacoesPendentes() {
        return prestadorRepository.findByStatusOrderByEnviadoEmAsc(StatusPrestador.PENDENTE);
    }

    @Transactional(readOnly = true)
    public Prestador buscarDetalhePublico(Long id) {
        return prestadorRepository.findByIdAndStatusAndAtivoTrue(id, StatusPrestador.ATIVO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
    }

    @Transactional(readOnly = true)
    public Prestador detalharSolicitacaoPendente(Long id) {
        return buscarSolicitacaoPendente(id);
    }

    @Transactional
    public Prestador aprovarSolicitacao(Long id, UUID adminId) {
        Prestador prestador = buscarSolicitacaoPendente(id);

        prestador.setStatus(StatusPrestador.ATIVO);
        prestador.setAtivo(true);
        prestador.setAprovadoEm(LocalDateTime.now());
        prestador.setAprovadoPor(adminId.toString());
        prestador.setMotivoRejeicao(null);

        return prestador;
    }

    @Transactional
    public Prestador reprovarSolicitacao(Long id, UUID adminId) {
        Prestador prestador = buscarSolicitacaoPendente(id);

        prestador.setStatus(StatusPrestador.REJEITADO);
        prestador.setAtivo(false);
        prestador.setAprovadoEm(null);
        prestador.setAprovadoPor(adminId.toString());
        prestador.setMotivoRejeicao(null);

        return prestador;
    }

    private Prestador buscarSolicitacaoPendente(Long id) {
        Prestador prestador = prestadorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));

        if (prestador.getStatus() != StatusPrestador.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solicitação não está pendente");
        }

        return prestador;
    }
}
