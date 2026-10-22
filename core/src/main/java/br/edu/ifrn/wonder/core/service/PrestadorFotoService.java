package br.edu.ifrn.wonder.core.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@Service
public class PrestadorFotoService {

    private final PrestadorRepository prestadorRepository;
    private final ArmazenamentoFotoService armazenamentoFotoService;
    private final String apiPublicUrl;

    public PrestadorFotoService(PrestadorRepository prestadorRepository,
            ArmazenamentoFotoService armazenamentoFotoService,
            @Value("${wonder.api.public-url}") String apiPublicUrl) {
        this.prestadorRepository = prestadorRepository;
        this.armazenamentoFotoService = armazenamentoFotoService;
        this.apiPublicUrl = apiPublicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Prestador atualizarFoto(Long prestadorId, UUID keycloakId, MultipartFile arquivo) {
        Prestador prestador = prestadorRepository.buscarPorIdComBloqueio(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para modificar este prestador");
        }
        if (prestador.getStatus() == StatusPrestador.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro pendente de análise não pode ser editado");
        }

        ValidadorFoto.validar(arquivo);
        armazenamentoFotoService.salvar(nomeObjeto(prestadorId), arquivo);
        prestador.setFotoUrl(apiPublicUrl + caminhoFoto(prestadorId));
        return prestador;
    }

    @Transactional(readOnly = true)
    public FotoPerfilConteudo buscarFoto(Long prestadorId, UUID keycloakId, boolean administrador) {
        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(this::fotoNaoEncontrada);
        boolean publicado = prestador.isAtivo() && prestador.getStatus() == StatusPrestador.ATIVO;
        boolean dono = prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId);
        if (!publicado && !dono && !administrador) {
            throw fotoNaoEncontrada();
        }
        if (prestador.getFotoUrl() == null || !prestador.getFotoUrl().endsWith(caminhoFoto(prestadorId))) {
            throw fotoNaoEncontrada();
        }
        return armazenamentoFotoService.buscar(nomeObjeto(prestadorId));
    }

    private String nomeObjeto(Long prestadorId) {
        return "prestadores/%s/foto".formatted(prestadorId);
    }

    private String caminhoFoto(Long prestadorId) {
        return "/catalogo/prestadores/%s/foto".formatted(prestadorId);
    }

    private ResponseStatusException fotoNaoEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto do prestador não encontrada");
    }
}
